import sbt._
import Keys._

object PlatformBuild extends Build {
  
  val debugging = false
  
  lazy val platformSettings = {
    Seq(
      scalaVersion := "2.10.2",
      libraryDependencies := Seq(
        "org.scala-lang" % "scala-library" % "2.10.2", // why do we need this here?        
        "commons-lang" % "commons-lang" % "2.6", 
        "org.mockito" % "mockito-all" % "1.9.0" % "unit, shared",
        "org.scalatest" % "scalatest_2.10" % "1.9.2" % "unit, shared"        
        )
    ) ++
    testSettings    
  }  

  lazy val Shared = config("shared") extend(Runtime)
  lazy val UnitTests = config("unit") extend(Shared)  

  lazy val testSettings = configure(Shared, Defaults.configSettings) ++ configure(UnitTests)

  private def configure(config: Configuration, settings: Seq[Setting[_]] = Defaults.testSettings) = {
    inConfig(config)(settings) ++
    (fork in config := !debugging) ++        
    (sourceDirectory in config <<= sourceDirectory in Test) ++
    (classDirectory in config <<= classDirectory in Test) ++    
    (sources in config ~= { _ filter { shouldInclude(_, config.name) } } ) ++
    (testOptions in config += Tests.Argument("-oDF")) ++
    (mappings in (config, Keys.packageBin) ~= { _.filter { case (file, path) => { file.getAbsolutePath.contains(config.name) || !file.getName.endsWith(".class") } } } )    
  }
    
  private def shouldInclude(file: File, testType: String) = {
    file.toURI.toString.contains("/" + testType + "/")
  }
  
  lazy val project = Project("platform", file("."))    
    .configs(Shared)
    .configs(UnitTests)
    .settings(platformSettings : _*)
      
}
