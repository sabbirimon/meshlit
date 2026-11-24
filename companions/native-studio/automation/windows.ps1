$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Add-Type -AssemblyName UIAutomationClient,UIAutomationTypes
$root = [System.Windows.Automation.AutomationElement]::RootElement
$all = [System.Windows.Automation.Condition]::TrueCondition
$children = [System.Windows.Automation.TreeScope]::Children
$walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
$receipts = @{}
function Windows {
    $list = @()
    foreach ($window in $root.FindAll($children,$all)) {
        try {
            $process = Get-Process -Id $window.Current.ProcessId
            if ($process.ProcessName -notin @('notepad','wordpad','mspaint','write')) { continue }
            $id = "$($process.Id)-$($window.Current.NativeWindowHandle)"
            $list += @{id=$id; name="$($process.ProcessName) · $($window.Current.Name)"; element=$window}
        } catch {}
    }
    return $list
}
function Inspect($node,$depth,$results,$app) {
    if ($depth -gt 10 -or $results.Count -ge 160) {return}
    try {
        if ($node.Current.IsPassword -or !$node.Current.IsEnabled) {return}
        $role = $node.Current.ControlType.ProgrammaticName
        $press = $node.GetCurrentPropertyValue([System.Windows.Automation.AutomationElement]::IsInvokePatternAvailableProperty)
        $edit = $role -eq 'ControlType.Edit' -and $node.GetCurrentPropertyValue([System.Windows.Automation.AutomationElement]::IsValuePatternAvailableProperty)
        if ($press -or $edit) {
            $key = [guid]::NewGuid().ToString()
            $receipts[$key]=@{app=$app; node=$node; until=[DateTime]::UtcNow.AddSeconds(60); name=$node.Current.Name; role=$role}
            [void]$results.Add(@{id=$key;role=$role;title=$node.Current.Name;press=[bool]$press;editable=[bool]$edit})
        }
        $c=$walker.GetFirstChild($node)
        while ($c -and $results.Count -lt 160 -and $script:visited -lt 512) { $script:visited++; Inspect $c ($depth+1) $results $app; $c=$walker.GetNextSibling($c) }
    } catch {}
}
while ($null -ne ($line=[Console]::ReadLine())) {
    $r=$null
    try {
        if ($line.Length -gt 32768) {throw 'Oversized request'}
        $r=ConvertFrom-Json -InputObject $line
        switch ($r.action) {
            'status' {$result=@{trusted=$true}}
            'apps' {$result=@{apps=@(Windows | ForEach-Object { @{id=$_.id;name=$_.name} })}}
            'snapshot' {
                $receipts.Clear(); $script:visited=0
                $win=@(Windows | Where-Object id -eq $r.bundle_id)
                if ($win.Count -ne 1) {throw 'Window closed'}
                $rows=[Collections.ArrayList]::new(); Inspect $win[0].element 0 $rows $r.bundle_id
                $result=@{nodes=$rows.ToArray()}
            }
            {$_ -in 'press','set_value'} {
                $old=$receipts[$r.node_id]; $receipts.Clear()
                if (!$old -or $old.app -ne $r.bundle_id -or $old.until -lt [DateTime]::UtcNow) {throw 'Select a fresh control'}
                $win=@(Windows | Where-Object id -eq $r.bundle_id)
                $node=$old.node
                if ($win.Count -ne 1 -or $node.Current.ProcessId -ne $win[0].element.Current.ProcessId -or $node.Current.IsPassword -or !$node.Current.IsEnabled -or $old.name -ne $node.Current.Name -or $old.role -ne $node.Current.ControlType.ProgrammaticName) {throw 'Control changed'}
                if ($r.action -eq 'press') {$node.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke()}
                else {
                    if ($old.role -ne 'ControlType.Edit' -or $r.text.Length -gt 8192) {throw 'Unsupported text field'}
                    $node.GetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern).SetValue([string]$r.text)
                }
                $result=@{ok=$true}
            }
            default {throw 'Unsupported action'}
        }
        $reply=@{id=$r.id;result=$result}
    } catch {$reply=@{id=$r.id;error=$_.Exception.Message}}
    [Console]::WriteLine((ConvertTo-Json -InputObject $reply -Compress -Depth 6))
}
