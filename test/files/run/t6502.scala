import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.ILoop
import scala.tools.nsc.settings.ClassPathRepresentationType
import scala.tools.partest._

object Test extends StoreReporterDirectTest {
  def code = ???

  def compileCode(code: String, jarFileName: String) = {
    val classpath = List(sys.props("partest.lib"), testOutput.path) mkString sys.props("path.separator")
    compileString(newCompiler("-cp", classpath, "-d", s"${testOutput.path}/$jarFileName"))(code)
  }

  // TODO flat classpath doesn't support the classpath invalidation yet so we force using the recursive one
  // it's the only test which needed such a workaround
  override def settings = {
    val settings = new Settings
    settings.YclasspathImpl.value = ClassPathRepresentationType.Recursive
    settings
  }

  def app1 = """
    package test

    object Test extends App {
      def test(): Unit = {
        println("testing...")
      }
    }"""

  def app2 = """
    package test

    object Test extends App {
      def test(): Unit = {
        println("testing differently...")
      }
    }"""

  def app3 = """
    package test

    object Test3 extends App {
      def test(): Unit = {
        println("new object in existing package")
      }
    }"""

  def app6 = """
    package test6
    class A extends Test { println("created test6.A") }
    class Z extends Test { println("created test6.Z") }
    trait Test"""

  def test1(): Unit = {
    val jar = "test1.jar"
    compileCode(app1, jar)

    val codeToRun = toCodeInSeparateLines(s":require ${testOutput.path}/$jar", "test.Test.test()")
    val output = ILoop.run(codeToRun, settings)
    val lines  = output.split("\n")
    assert {
      lines(4).contains("Added") && lines(4).contains("test1.jar")
    }
    assert {
      lines(lines.length-3).contains("testing...")
    }
  }

  def test2(): Unit = {
    // should reject jars with conflicting entries
    val jar1 = "test1.jar"
    val jar2 = "test2.jar"
    compileCode(app2, jar2)

    val codeToRun = toCodeInSeparateLines(s":require ${testOutput.path}/$jar1", s":require ${testOutput.path}/$jar2")
    val output = ILoop.run(codeToRun, settings)
    val lines  = output.split("\n")
    assert {
      lines(4).contains("Added") && lines(4).contains("test1.jar")
    }
    assert {
      lines(lines.length-3).contains("test2.jar") && lines(lines.length-3).contains("existing classpath entries conflict")
    }
  }

  def test3(): Unit = {
    // should accept jars with overlapping packages, but no conflicts
    val jar1 = "test1.jar"
    val jar3 = "test3.jar"
    compileCode(app3, jar3)

    val codeToRun = toCodeInSeparateLines(s":require ${testOutput.path}/$jar1", s":require ${testOutput.path}/$jar3", "test.Test3.test()")
    val output = ILoop.run(codeToRun, settings)
    val lines  = output.split("\n")
    assert {
      lines(4).contains("Added") && lines(4).contains("test1.jar")
    }
    assert {
      lines(lines.length-3).contains("new object in existing package")
    }
  }

  def test4(): Unit = {
    // twice the same jar should be rejected
    val jar1   = "test1.jar"
    val codeToRun = toCodeInSeparateLines(s":require ${testOutput.path}/$jar1", s":require ${testOutput.path}/$jar1")
    val output = ILoop.run(codeToRun, settings)
    val lines  = output.split("\n")
    assert {
      lines(4).contains("Added") && lines(4).contains("test1.jar")
    }
    assert {
      lines(lines.length-3).contains("test1.jar") && lines(lines.length-3).contains("existing classpath entries conflict")
    }
  }

  def test5(): Unit = {
    val codeToRun = ":require /does/not/exist.jar"
    val output = ILoop.run(codeToRun, settings)
    assert(!output.contains("NullPointerException"), output)
    assert(output.contains("Cannot load '/does/not/exist.jar'"), output)
  }

  def test6(): Unit = {
    // Avoid java.lang.NoClassDefFoundError triggered by the old approach of using a Java
    // classloader to parse .class files in order to read their names.
    val jar = "test6.jar"
    compileCode(app6, jar)
    val codeToRun = toCodeInSeparateLines(s":require ${testOutput.path}/$jar", "import test6._; new A; new Z")
    val output = ILoop.run(codeToRun, settings)
    assert(output.contains("created test6.A"), output)
    assert(output.contains("created test6.Z"), output)
  }

  def show(): Unit = {
    test1()
    test2()
    test3()
    test4()
    test5()
    test6()
  }

  def toCodeInSeparateLines(lines: String*): String = lines mkString "\n"
}
