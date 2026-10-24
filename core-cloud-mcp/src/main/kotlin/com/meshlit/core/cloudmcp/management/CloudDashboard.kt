package com.meshlit.core.cloudmcp.management

import kotlinx.serialization.json.*

data class CloudDashboardSummary(val resources:List<Pair<String,String>> = emptyList(),val metrics:List<Pair<String,String>> = emptyList())
/** Only fields present in vendor responses become dashboard values; missing values stay absent. */
object CloudDashboard {
    private fun JsonObject.text(name:String)=(get(name) as? JsonPrimitive)?.contentOrNull
    fun summary(vendor:CloudVendor,action:String,data:JsonElement):CloudDashboardSummary {
        val obj=data as? JsonObject ?: return CloudDashboardSummary()
        fun records(name:String)=((obj[name] as? JsonArray).orEmpty()).mapNotNull{it as? JsonObject}
        fun resourceRows(records:List<JsonObject>,id:String,label:String)=records.mapNotNull{record->record.text(id)?.let{it to (record.text(label) ?: it)}}.take(100)
        return when(vendor){
            CloudVendor.AWS->if(action=="instances") CloudDashboardSummary((obj["instanceId"] as? JsonArray).orEmpty().mapNotNull{(it as? JsonPrimitive)?.contentOrNull}.take(100).map{it to it}) else if(action=="costs"){
                val metrics=records("ResultsByTime").flatMap{row->
                    val totals=row["Total"] as? JsonObject
                    totals.orEmpty().mapNotNull{(metric,value)->val valueObj=value as? JsonObject;valueObj?.text("Amount")?.let{metric to "$it ${valueObj.text("Unit").orEmpty()}"}}+
                        listOfNotNull(row.text("Estimated")?.let{"Provider estimated" to it})
                };CloudDashboardSummary(metrics=metrics)
            }else CloudDashboardSummary(metrics=listOfNotNull((obj["Account"] as? JsonArray)?.firstOrNull()?.let{"Account" to it.jsonPrimitive.content}))
            CloudVendor.AZURE->if(action=="costs"){
                val properties=obj["properties"] as? JsonObject ?: return CloudDashboardSummary()
                val columns=(properties["columns"] as? JsonArray).orEmpty().map{(it as? JsonObject)?.text("name")}
                val rows=(properties["rows"] as? JsonArray).orEmpty()
                CloudDashboardSummary(metrics=rows.take(20).flatMap{row->(row as? JsonArray).orEmpty().mapIndexedNotNull{index,value->columns.getOrNull(index)?.let{it to value.toString().trim('"')}}})
            }else CloudDashboardSummary(resourceRows(records("value"),if(action=="subscriptions") "subscriptionId" else "id",if(action=="subscriptions") "displayName" else "name"))
            CloudVendor.DIGITALOCEAN->if(action=="balance") CloudDashboardSummary(metrics=listOf("account_balance","month_to_date_balance","month_to_date_usage","generated_at").mapNotNull{key->obj.text(key)?.let{key to it}}) else CloudDashboardSummary(resourceRows(records("droplets"),"id","name"))
            CloudVendor.GCP->when(action){
                "projects"->CloudDashboardSummary(resourceRows(records("projects"),"projectId","name"))
                "instances"->{val items=obj["items"] as? JsonObject;val rows=items.orEmpty().values.flatMap{zone->((zone as? JsonObject)?.get("instances") as? JsonArray).orEmpty().mapNotNull{it as? JsonObject}};CloudDashboardSummary(resourceRows(rows,"id","name"))}
                else->CloudDashboardSummary(metrics=listOf("billingEnabled","billingAccountName","projectId").mapNotNull{key->obj.text(key)?.let{key to it}})
            }
            CloudVendor.OPENROUTER->if(action=="models") CloudDashboardSummary(resourceRows(records("data"),"id","name")) else{
                val info=obj["data"] as? JsonObject
                CloudDashboardSummary(metrics=listOf("usage","usage_monthly","limit","limit_remaining","is_free_tier").mapNotNull{key->info?.text(key)?.let{key to it}})
            }
            CloudVendor.CUSTOM->CloudDashboardSummary()
        }
    }
}
