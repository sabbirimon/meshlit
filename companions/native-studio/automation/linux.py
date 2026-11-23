"""AT-SPI controls for selected local graphical editors; JSON lines on standard I/O."""
import json
import os
import secrets
import sys
import time
import gi
gi.require_version("Atspi", "2.0")
from gi.repository import Atspi, GLib

receipts = {}
EDITORS = {"gedit","gnome-text-editor","kate","kwrite","mousepad","xed","leafpad","pluma"}
def apps():
    desktop = Atspi.get_desktop(0)
    result = {}
    for i in range(min(256,desktop.get_child_count())):
        app = desktop.get_child_at_index(i)
        try:
            pid = app.get_process_id()
            name = os.path.basename(os.readlink(f"/proc/{pid}/exe"))
            if name in EDITORS:
                result[str(pid)] = (app,name)
        except (OSError, AttributeError, GLib.Error):
            continue
    return result

def inspect(app, identity):
    stack = [(app,0)]
    rows = []
    for _ in range(512):
        if not stack or len(rows)>=160: break
        node,depth=stack.pop()
        if not node: continue
        role=node.get_role()
        if role==Atspi.Role.PASSWORD_TEXT: continue
        editable=bool(node.get_editable_text_iface()) and node.get_state_set().contains(Atspi.StateType.EDITABLE)
        button=role in (Atspi.Role.PUSH_BUTTON,Atspi.Role.TOGGLE_BUTTON) and node.get_action_iface() and node.get_n_actions()>0
        if editable or button:
            key=secrets.token_hex(16);title=(node.get_name() or "")[:160]
            receipts[key]=(node,identity,role,title,time.monotonic()+60,bool(button),editable)
            rows.append(dict(id=key,title=title,role=node.get_role_name(),press=bool(button),editable=editable))
        if depth<10:
            stack.extend((node.get_child_at_index(i),depth+1) for i in reversed(range(min(node.get_child_count(),80))))
    return rows

def handle(r):
    if r["action"]=="status": return {"trusted":Atspi.get_desktop(0) is not None}
    windows=apps()
    if r["action"]=="apps":return {"apps":[{"id":k,"name":v[1]} for k,v in windows.items()]}
    identity=r["bundle_id"]
    if identity not in windows:raise ValueError("The selected editor closed")
    if r["action"]=="snapshot":
        receipts.clear()
        return {"nodes":inspect(windows[identity][0],identity)}
    receipt=receipts.get(r["node_id"]);receipts.clear()
    if not receipt:raise ValueError("Select a fresh control")
    node,owner,role,title,expiry,button,editable=receipt
    if owner!=identity or expiry<time.monotonic() or node.get_role()!=role or (node.get_name() or "")[:160]!=title or node.get_process_id()!=int(identity):
        raise ValueError("Control changed")
    if r["action"]=="press" and button: ok=node.do_action(0)
    elif r["action"]=="set_value" and editable and len(r["text"].encode())<=8192: ok=node.set_text_contents(r["text"])
    else: raise ValueError("Unsupported control action")
    if not ok:raise ValueError("The application declined the action")
    return {"ok":True}

for line in sys.stdin:
    request={}
    try:
        if len(line)>32768:raise ValueError("Oversized request")
        request=json.loads(line);reply={"id":request["id"],"result":handle(request)}
    except Exception as exc:reply={"id":request.get("id"),"error":str(exc)}
    print(json.dumps(reply),flush=True)
