/* SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0 */
package app.infinity.mpvz.ui.player
import android.content.Context
import android.util.Log
import app.infinity.mpvz.domain.hdr.MpvShaderRuntime
import java.io.File
internal data class RexShaderApplyResult(val activeAlgorithms: Set<String>, val unavailable: Map<String,String>, val activePasses: Set<String>)
/** Owns only REX shader paths it appends; unrelated user/HDR/Anime4K shader entries are preserved. */
internal class RexVisionShaderManager(context: Context, private val runtime: MpvShaderRuntime) {
 private val app=context.applicationContext
 private val dir=File(app.filesDir,"shaders/rex-vision")
 private val files=linkedMapOf("texture-denoise" to "rex_texture_aware_denoise.glsl","compression-cleanup" to "rex_compression_cleanup.glsl","edge-sharpen" to "rex_edge_adaptive_sharpen.glsl","local-tone" to "rex_local_tone_map.glsl","highlight-control" to "rex_highlight_control.glsl")
 private val installed=linkedSetOf<String>()
 private var ready=false
 private var failure:String?=null
 @Synchronized fun apply(ids:Set<String>,hdr:Boolean):RexShaderApplyResult {
  val requested=RexVisionPolicy.algorithms.filter{it.id in ids&&it.shaderPasses.isNotEmpty()}
  val unavailable=linkedMapOf<String,String>()
  val allowed=requested.filter{a->val sensitive=a.shaderPasses.any{it=="local-tone"||it=="highlight-control"};if(hdr&&sensitive){unavailable[a.id]="Skipped for PQ/HLG HDR input to preserve native HDR colour.";false}else true}
  val wanted=files.keys.filter{pass->allowed.any{pass in it.shaderPasses}}
  if(wanted.isNotEmpty()&&!ensureAssets()){val why=failure?:"REX shader assets could not be installed.";allowed.forEach{unavailable[it.id]=why};clear();return RexShaderApplyResult(emptySet(),unavailable,emptySet())}
  val paths=wanted.map{File(dir,files.getValue(it)).absolutePath}.toSet()
  installed.toList().asReversed().filterNot{it in paths}.forEach(::removePath)
  val failed=linkedSetOf<String>()
  for(pass in wanted){val path=File(dir,files.getValue(pass)).absolutePath;if(!listed(path)){val ok=runCatching{runtime.command("change-list","glsl-shaders","append",path);listed(path)}.getOrDefault(false);if(ok)installed+=path else{failed+=pass;failure="MPV did not confirm shader registration for $pass.";runCatching{runtime.command("change-list","glsl-shaders","remove",path)}}}else installed+=path}
  allowed.filter{a->a.shaderPasses.any{it in failed}}.forEach{unavailable[it.id]=failure?:"Required REX shader pass was rejected."}
  val activePasses=wanted.filter{it !in failed&&listed(File(dir,files.getValue(it)).absolutePath)}.toSet()
  val active=allowed.filter{a->a.id !in unavailable&&a.shaderPasses.all{it in activePasses}}.map{it.id}.toSet()
  return RexShaderApplyResult(active,unavailable,activePasses)
 }
 @Synchronized fun clear(){installed.toList().asReversed().forEach(::removePath);installed.clear()}
 private fun listed(path:String)=runtime.getPropertyString("glsl-shaders")?.split(':')?.any{it.trim()==path}==true
 private fun removePath(path:String){runCatching{runtime.command("change-list","glsl-shaders","remove",path)};installed.remove(path)}
 private fun ensureAssets():Boolean{
  if(ready&&files.values.all{File(dir,it).isFile&&File(dir,it).length()>0L})return true
  return runCatching{if(!dir.exists()&&!dir.mkdirs())error("Could not create REX shader directory.");files.values.forEach{name->val bytes=app.assets.open("shaders/rex-vision/$name").use{it.readBytes()};require(bytes.isNotEmpty()){"Empty REX shader asset: $name"};File(dir,name).writeBytes(bytes)};ready=files.values.all{File(dir,it).isFile&&File(dir,it).length()>0L};require(ready){"REX shader assets missing."};true}.onFailure{e->ready=false;failure=e.message?:"Could not copy REX GLSL assets.";Log.w("RexVisionShaderManager","Shader setup failed",e)}.getOrDefault(false)
 }
}
