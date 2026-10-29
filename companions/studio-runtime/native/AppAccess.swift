// Meshlit's bounded macOS Accessibility adapter. No screen capture, shell or keychain access.
import AppKit
import ApplicationServices
import CryptoKit

struct AccessFailure: Error { let message: String }
func fail(_ text: String) throws -> Never { throw AccessFailure(message: text) }
func attr(_ node: AXUIElement, _ key: String) -> CFTypeRef? {
    var value: CFTypeRef?
    return AXUIElementCopyAttributeValue(node, key as CFString, &value) == .success ? value : nil
}
func string(_ node: AXUIElement, _ key: String) -> String { String((attr(node,key) as? String ?? "").prefix(1024)) }
func secure(_ node: AXUIElement) -> Bool { string(node,kAXSubroleAttribute) == "AXSecureTextField" }
func signature(_ node: AXUIElement) -> String {
    let text=[kAXRoleAttribute,kAXSubroleAttribute,kAXIdentifierAttribute,kAXTitleAttribute,kAXDescriptionAttribute,kAXValueAttribute].map{string(node,$0)}.joined(separator:"\u{0}")
    return SHA256.hash(data:Data(text.utf8)).map{String(format:"%02x",$0)}.joined()
}
let blocked:Set<String> = ["com.apple.systempreferences","com.apple.SecurityAgent","com.apple.keychainaccess","com.apple.Passwords","com.apple.loginwindow","com.apple.AuthorizationHost"]
struct Receipt { let element: AXUIElement; let signature: String; let bundle: String; let pid: pid_t; let time: Date }
var receipts=[String:Receipt]()
func app(_ bundle:String) throws -> NSRunningApplication {
    guard !blocked.contains(bundle), bundle != "com.meshlit.desktop", bundle.count<=180,
          bundle.range(of:"^[A-Za-z0-9][A-Za-z0-9.-]+$",options:.regularExpression) != nil else {try fail("This app requires human control")}
    let matches=NSRunningApplication.runningApplications(withBundleIdentifier:bundle).filter{!$0.isTerminated}
    guard matches.count == 1, let value=matches.first else {try fail("Choose an app with exactly one running instance")}
    return value
}
func handle(_ input:[String:Any]) throws -> [String:Any] {
    let operation=input["action"] as? String ?? ""
    if operation == "status" { return ["trusted":AXIsProcessTrusted(),"platform":"macOS","capabilities":["snapshot","press","set_value"]] }
    if operation == "request_permission" {
        let options=[kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String:true] as CFDictionary
        return ["trusted":AXIsProcessTrustedWithOptions(options),"message":"The device owner must enable Meshlit App Access in macOS Privacy & Security → Accessibility."]
    }
    if operation == "apps" {
        return ["apps":NSWorkspace.shared.runningApplications.filter{$0.activationPolicy == .regular && !blocked.contains($0.bundleIdentifier ?? "") && $0.bundleIdentifier != "com.meshlit.desktop"}.compactMap{a -> [String:String]? in
            guard let id=a.bundleIdentifier else{return nil}; return ["id":id,"name":a.localizedName ?? id]
        }.prefix(100).map{$0}]
    }
    if operation == "stop" { receipts.removeAll();return ["stopped":true] }
    guard AXIsProcessTrusted() else {try fail("Accessibility permission is not enabled for Meshlit App Access")}
    let bundle=input["bundle_id"] as? String ?? ""
    let target=try app(bundle)
    if operation == "snapshot" {
        receipts.removeAll()
        let root=AXUIElementCreateApplication(target.processIdentifier)
        AXUIElementSetMessagingTimeout(root,2)
        var rows=[[String:Any]](); var remaining=180
        func walk(_ node:AXUIElement,_ depth:Int) {
            guard remaining>0, depth<=10 else{return}; remaining-=1
            if secure(node) { return }
            let id=UUID().uuidString, role=string(node,kAXRoleAttribute)
            var actions:CFArray?; AXUIElementCopyActionNames(node,&actions)
            var editable:DarwinBoolean=false; AXUIElementIsAttributeSettable(node,kAXValueAttribute as CFString,&editable)
            rows.append(["id":id,"role":role,"title":string(node,kAXTitleAttribute),"description":string(node,kAXDescriptionAttribute),"value":string(node,kAXValueAttribute),"press":(actions as? [String] ?? []).contains(kAXPressAction),"editable":editable.boolValue,"depth":depth])
            receipts[id]=Receipt(element:node,signature:signature(node),bundle:bundle,pid:target.processIdentifier,time:Date())
            for child in (attr(node,kAXChildrenAttribute) as? [AXUIElement] ?? []).prefix(180) {walk(child,depth+1)}
        }
        walk(root,0)
        return ["bundle_id":bundle,"nodes":rows,"truncated":remaining==0,"expiresAfterSeconds":60]
    }
    guard ["press","set_value"].contains(operation), let id=input["node_id"] as? String, let receipt=receipts[id], receipt.bundle==bundle,
          receipt.pid==target.processIdentifier, Date().timeIntervalSince(receipt.time)<60, !secure(receipt.element), signature(receipt.element)==receipt.signature else {try fail("Stale or invalid target; take a fresh snapshot")}
    // Consume all handles before changing the UI, including when the action fails.
    receipts.removeAll()
    let result:AXError
    if operation == "press" { result=AXUIElementPerformAction(receipt.element,kAXPressAction as CFString) }
    else {
        guard let text=input["text"] as? String,text.utf8.count<=8192 else {try fail("Text is missing or exceeds 8 KiB")}
        result=AXUIElementSetAttributeValue(receipt.element,kAXValueAttribute as CFString,text as CFString)
    }
    guard result == .success else {try fail("The app did not accept this accessibility action")}
    return ["ok":true,"message":"Action accepted by macOS; take a fresh snapshot to verify its effect."]
}
while let line=readLine() {
    var output:[String:Any]=[:]
    do {
        guard line.utf8.count<=32768, let data=line.data(using:.utf8),let request=try JSONSerialization.jsonObject(with:data) as? [String:Any] else {try fail("Invalid request")}
        output["id"]=request["id"]
        output["result"]=try handle(request)
    } catch let error as AccessFailure {output["error"]=error.message}
      catch {output["error"]="Accessibility request failed"}
    if let data=try? JSONSerialization.data(withJSONObject:output,options:[.sortedKeys]),let text=String(data:data,encoding:.utf8){print(text);fflush(stdout)}
}
