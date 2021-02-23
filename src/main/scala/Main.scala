import cats.effect.{IO, ExitCode, IOApp}
import cats.Parallel
import application.Application
import scala.concurrent.ExecutionContext

object Main extends IOApp {
  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val parallel: Parallel[IO] = IO.ioParallel

  override def run(args: List[String]): IO[ExitCode] = new Application().execute()

}
