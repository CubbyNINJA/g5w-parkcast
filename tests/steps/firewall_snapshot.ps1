# Every Windows Firewall rule with its program and port filters, as JSON. Read-only.
# Used by tests/steps/live_steps.py as an oracle that's independent of the code under test.
# Needs an elevated terminal (reading port filters is refused otherwise), like the runner itself,
# which creates and removes firewall rules. Any error stops it, so partial output can't pass.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$apps = @{}
Get-NetFirewallApplicationFilter -All | ForEach-Object { $apps[$_.InstanceID] = $_.Program }
$ports = @{}
Get-NetFirewallPortFilter -All | ForEach-Object { $ports[$_.InstanceID] = "$($_.Protocol)/$($_.LocalPort)" }
Get-NetFirewallRule | ForEach-Object {
    [pscustomobject]@{
        Name        = $_.Name
        DisplayName = $_.DisplayName
        Enabled     = "$($_.Enabled)"
        Direction   = "$($_.Direction)"
        Action      = "$($_.Action)"
        Profile     = "$($_.Profile)"
        Program     = "$($apps[$_.InstanceID])"
        Ports       = "$($ports[$_.InstanceID])"
    }
} | ConvertTo-Json -Compress
