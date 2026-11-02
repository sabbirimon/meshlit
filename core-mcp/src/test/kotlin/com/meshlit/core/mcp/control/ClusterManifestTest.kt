package com.meshlit.core.mcp.control
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class ClusterManifestTest {
    @Test fun selectorsRespectApprovalFreshnessAndConflicts() {
        val spec=ClusterManifest(name="home",revision=1,groups=listOf(ClusterGroup("compute",cpu=DesiredCpuConfiguration(threads=4))))
        val node=ObservedClusterNode("phone",true,setOf("resource-read"),8,1000,3,DesiredCpuConfiguration(threads=2))
        val plan=DeclarativeClusterController.preview(spec,listOf(node),2000)
        assertEquals(3L,plan.changes.single().expectedRevision)
        assertTrue(DeclarativeClusterController.preview(spec,listOf(node.copy(approved=false)),2000).changes.isEmpty())
        assertTrue(DeclarativeClusterController.preview(spec,listOf(node),32000).blocked.isNotEmpty())
        assertTrue(DeclarativeClusterController.preview(spec.copy(groups=spec.groups+ClusterGroup("other",cpu=DesiredCpuConfiguration(threads=2))),listOf(node),2000).blocked.isNotEmpty())
    }
    @Test fun dynamicRestoreRequiresTheExactBaseAndReconstructsRemovedFields() {
        val a=buildJsonObject{put("mode","BALANCED");put("old",true)};val b=buildJsonObject{put("mode","OVERDRIVE")}
        val delta=ConfigurationSnapshots.delta(a,b);assertEquals(b,ConfigurationSnapshots.restore(a,delta))
        assertThrows(IllegalArgumentException::class.java){ConfigurationSnapshots.restore(b,delta)}
    }
}
