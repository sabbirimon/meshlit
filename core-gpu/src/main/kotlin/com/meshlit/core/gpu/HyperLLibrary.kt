package com.meshlit.core.gpu

/** Original bounded preprocessing recipes; every lookup returns fresh examples. */
data class HyperLRecipe(val id:String,val title:String,val purpose:String,val program:HyperLProgram,val example:Map<String,FloatArray>) {
    val elementwiseOnly:Boolean get()=program.instructions.none{it.operation=="sum"}
}
object HyperLLibrary {
    val ids:List<String> get()=listOf("add","multiply","relu","weighted_relu","residual_relu","affine","affine_relu","residual_affine_relu","dot","sum","positive_sum","squared_norm")
    fun recipe(id:String):HyperLRecipe {
        fun step(name:String,op:String,vararg args:String)=HyperLInstruction(name,op,args.toList())
        val x=floatArrayOf(-1f,2f,3f);val w=floatArrayOf(2f,3f,4f)
        val y=floatArrayOf(4f,-3f,1f);val bias=floatArrayOf(1f,1f,1f);val residual=floatArrayOf(1f,-1f,0f)
        val values:Map<String,FloatArray>;val steps:List<HyperLInstruction>;val title:String;val purpose:String
        when(id){
            "add"->{title="Vector add";purpose="Combine feature vectors";values=mapOf("x" to x,"y" to y);steps=listOf(step("value","add","x","y"))}
            "multiply"->{title="Vector multiply";purpose="Apply per-feature weights or masks";values=mapOf("x" to x,"w" to w);steps=listOf(step("value","multiply","x","w"))}
            "relu"->{title="ReLU";purpose="Remove negative feature values";values=mapOf("x" to x);steps=listOf(step("positive","relu","x"))}
            "weighted_relu"->{title="Weighted ReLU";purpose="Weighted feature preprocessing";values=mapOf("x" to x,"w" to w);steps=listOf(step("value","multiply","x","w"),step("positive","relu","value"))}
            "residual_relu"->{title="Residual ReLU";purpose="Combine an existing feature branch";values=mapOf("x" to x,"residual" to residual);steps=listOf(step("value","add","x","residual"),step("positive","relu","value"))}
            "affine","affine_relu","residual_affine_relu"->{
                title=when(id){"affine"->"Affine features";"affine_relu"->"Affine ReLU";else->"Residual affine ReLU"};purpose="Per-feature scale, bias and optional residual/activation"
                values=mapOf("x" to x,"w" to w,"bias" to bias)+(if(id=="residual_affine_relu")mapOf("residual" to residual)else emptyMap())
                steps=listOf(step("product","multiply","x","w"),step("shifted","add","product","bias"))+
                    (if(id=="residual_affine_relu")listOf(step("combined","add","shifted","residual"))else emptyList())+
                    (if(id!="affine")listOf(step("positive","relu",if(id=="residual_affine_relu")"combined" else "shifted"))else emptyList())
            }
            "dot"->{title="Dot product";purpose="Ordered feature similarity; not cosine normalization";values=mapOf("x" to x,"w" to w);steps=listOf(step("product","multiply","x","w"),step("total","sum","product"))}
            "sum"->{title="Ordered sum";purpose="Bounded scalar aggregation";values=mapOf("x" to x);steps=listOf(step("total","sum","x"))}
            "positive_sum"->{title="Positive sum";purpose="Aggregate positive activations";values=mapOf("x" to x);steps=listOf(step("positive","relu","x"),step("total","sum","positive"))}
            "squared_norm"->{title="Squared norm";purpose="Feature energy; no square root or normalization";values=mapOf("x" to x);steps=listOf(step("squared","multiply","x","x"),step("total","sum","squared"))}
            else->error("Unknown HyperL recipe: $id")
        }
        return HyperLRecipe(id,title,purpose,HyperLProgram(inputs=values.keys.toSet(),instructions=steps,output=steps.last().output).also{it.validate()},values)
    }
}
