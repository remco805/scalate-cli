package net.stbbs.yasushi

import java.io.File
import org.slf4j.LoggerFactory
import org.fusesource.scalate.Binding
import org.fusesource.scalate.DefaultRenderContext
import org.fusesource.scalate.TemplateEngine
import org.fusesource.scalate.TemplateSource
import org.fusesource.scalate.servlet.ServletRenderContext
import org.fusesource.scalate.support.FileResourceLoader
import org.fusesource.scalate.util.IOUtil

import org.github.scopt.OptionParser

import scala.collection.mutable.ListBuffer

object config {
  var useServlet = false
  var output:File = _
  var sources:ListBuffer[File] = ListBuffer()

  def valid = {
    val result =
      output != null
    if (!result)
      System.err.println("Error: missing arguments: -o")
    result
  }
}

object ScalatePrecompiler {
  val logger = LoggerFactory.getLogger("ScalateCli")

  def parse(args: Array[String]) {
    val parser = new OptionParser("ScalatePrecompiler") {
      opt("servlet", "use ServletRenderContext instead of DefaultRenderContext", {config.useServlet = true})
      opt("o", "output", "<output directory>","", {s: String => config.output=new File(s)})
      arglist("<source directory>...", "", {s: String => config.sources += new File(s)})
    }
    if (!parser.parse(args) || !config.valid)
      exit
  }

  def main(args: Array[String]) {
    parse(args)

    import config._

    logger.debug("outputDirectory: {}", output)
    logger.debug("sourceDirectories: {}", sources)

    output.mkdirs()

    // TODO need to customize bindings
    var engine = new TemplateEngine
    engine.bindings = createBindings()

    engine.resourceLoader = new FileResourceLoader(None)

    val paths = sources.flatMap(sd => collect(sd, "", engine.codeGenerators.keySet))

    logger.info("Precompiling Scalate Templates into Scala sources...")

    for ((uri, file) <- paths) {
      val sourceFile = new File(output, uri.replace(':', '_') + ".scala")
      if (changed(file, sourceFile)) {
        logger.info("    processing {} (uri: {})", file, uri)
        val code = engine.generateScala(TemplateSource.fromFile(file, uri), createBindingsForPath(uri))
        sourceFile.getParentFile.mkdirs
        IOUtil.writeBinaryFile(sourceFile, code.source.getBytes("UTF-8"))
      }
    }
  }

  def collect(basedir: File, baseuri: String, exts: collection.Set[String]): Map[String, File] = {
    // TODO check same uri WEB-INF and other directory.
    def uri(d: File) = baseuri + (if (d.getName != "WEB-INF") "/" + d.getName else "")
    if (basedir.isDirectory) {
      val files = basedir.listFiles.toList
      val dirs = files.filter(_.isDirectory)
      files.filter(f => exts(f.getName.split("\\.").last)).map(f => (baseuri + "/" + f.getName, f)).toMap ++ dirs.flatMap(d => collect(d, uri(d), exts))
    } else
      Map.empty
  }

  lazy val renderContextClassName =
    if (config.useServlet)
      classOf[ServletRenderContext].getName
    else
      classOf[DefaultRenderContext].getName

  def createBindings(): List[Binding] =
    List(Binding("context", renderContextClassName, true, isImplicit = true))
  def createBindingsForPath(uri:String): List[Binding] = Nil

  def changed(template: File, source: File) =
    !(source.exists && template.lastModified < source.lastModified)

}
