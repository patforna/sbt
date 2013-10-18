import _root_.sbt.IO
import java.lang.management.ManagementFactory
import java.lang.System._
import java.net.URL
import sbt._
import sbt.Configuration
import sbt.Keys._
import scala._
import scala.collection.JavaConversions._

object BuildSettings {
  import Project._
  import Defaults._

  val buildVersion = Option(System.getenv("GO_PIPELINE_LABEL")).getOrElse("LOCAL")
  val debugging: Boolean = ManagementFactory.getRuntimeMXBean().getInputArguments().exists(_ == "-Xdebug")

  lazy val buildSettings = {
    Seq(
      scalaVersion := "2.10.2",
      libraryDependencies := Seq("org.scalatest" % "scalatest_2.10" % "1.9.2" % "unit, integration, functional, shared")      
    ) ++ sharedSettings ++ unitTestSettings ++ integrationTestSettings ++ functionalTestSettings
  }

  lazy val Shared = config("shared") extend(Runtime) // FIXME could we rename this to test?
  lazy val sharedSettings = createTestSettings("shared", Shared, Defaults.configSettings)

  lazy val UnitTests = config("unit") extend(Shared)
  lazy val unitTestSettings = createTestSettings("unit", UnitTests)

  lazy val IntegrationTests = config("integration") extend(Shared)
  lazy val integrationTestSettings = createTestSettings("integration", IntegrationTests)

  lazy val FunctionalTests = config("functional") extend(Shared)
  lazy val functionalTestSettings = createTestSettings("functional", FunctionalTests)
  
  lazy val SystemTests = config("system") extend(Shared)
  lazy val systemTestSettings = createTestSettings("system", SystemTests)  

  private def createTestSettings(testType: String, testConfiguration: Configuration, settings: Seq[Setting[_]] = Defaults.testSettings) = {
    inConfig(testConfiguration)(settings) ++
    ((fork in testConfiguration) := !debugging) ++    
    (sourceDirectory in testConfiguration <<= sourceDirectory in Test) ++
    (classDirectory in testConfiguration <<= classDirectory in Test) ++    
    (sources in testConfiguration ~= { _ filter { shouldInclude(_, testType) } } ) ++
    (testOptions in testConfiguration += Tests.Argument("-oDF"))
  }
    
  private def shouldInclude(testFile: File, testType: String) = {
    testFile.toURI.toString.contains("/" + testType + "/")
  }  
  
  private def createPackageSettings(testType: String, testConfiguration: Configuration, packagingTaskKey: TaskKey[sbt.File]) = {
    inConfig(testConfiguration)(Defaults.testSettings) ++
     Defaults.packageTaskSettings(packagingTaskKey, mappings in (Test, packageBin)) ++
      (artifactName in packagingTaskKey := {(scalaversion, moduleId, artifact) => "%s_%s-%s-%s.jar" format (moduleId.name, scalaversion.binary, buildVersion, testType)}) ++
      (mappings in packagingTaskKey ~= { (ms: Seq[(File,String)]) =>
        ms.filter { case (file, path) =>{ file.getName.endsWith(".class")==false || file.getAbsolutePath.contains(testType) }
        }
      })
  }
  
}

object CasperBuild extends Build {

  import BuildSettings._

  lazy val core = Project("core", file("."))  
    .configs(Shared)
    .configs(UnitTests)
    .configs(IntegrationTests)
    .configs(FunctionalTests)
    .configs(SystemTests)
    .settings(buildSettings : _*)
}

