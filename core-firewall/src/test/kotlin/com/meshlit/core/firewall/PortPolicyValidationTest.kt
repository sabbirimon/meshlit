package com.meshlit.core.firewall
import org.junit.Assert.*
import org.junit.Test
class PortPolicyValidationTest {
    @Test fun savedPolicyCannotContainMalformedOrDuplicateRules(){val rule=PortRule("test",portSpec=PortSpec.Single(18792));PortLayerPolicy(listOf(rule)).validate();assertThrows(IllegalArgumentException::class.java){PortLayerPolicy(listOf(rule,rule)).validate()};assertThrows(IllegalArgumentException::class.java){PortLayerPolicy(listOf(rule.copy(portSpec=PortSpec.Single(0)))).validate()};assertThrows(IllegalArgumentException::class.java){PortLayerPolicy(listOf(rule.copy(portSpec=PortSpec.Range(40,30)))).validate()}}
    @Test fun portAllowCannotOverrideAddressDeny(){val firewall=MeshlitFirewall(FirewallPolicy.Default,PortLayerPolicy(listOf(PortRule("test",portSpec=PortSpec.Single(18792)))));assertTrue(firewall.decide("192.168.1.4",null,null,18792).allowed);assertFalse(firewall.decide("8.8.8.8",null,null,18792).allowed);firewall.portLayer=PortLayerPolicy();assertFalse(firewall.decide("192.168.1.4",null,null,18792).allowed)}
}
