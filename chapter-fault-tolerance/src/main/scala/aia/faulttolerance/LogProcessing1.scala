package aia.faulttolerance

import java.io.File
import java.util.UUID
import akka.actor._
import akka.actor.SupervisorStrategy.{ Stop, Resume, Restart, Escalate }
import akka.actor.OneForOneStrategy
import scala.concurrent.duration._
import language.postfixOps

/**
 * A -> B,C,D
 * A创建B,C,D子actor,并监视他们，负责他们的异常流程的处理
 */
package dbstrategy1 {

  object LogProcessingApp extends App {
    val sources = Vector("file:///source1/", "file:///source2/")
    val system = ActorSystem("logprocessing")

    val databaseUrl = "http://mydatabase1"
    
    system.actorOf(
      LogProcessingSupervisor.props(sources, databaseUrl), 
      LogProcessingSupervisor.name
    )
  }


  /**
   * 最顶层的监视actor
   */
  object LogProcessingSupervisor {
    def props(sources: Vector[String], databaseUrl: String) =
      Props(new LogProcessingSupervisor(sources, databaseUrl))
    def name = "file-watcher-supervisor" 
  }

  class LogProcessingSupervisor(
    sources: Vector[String], 
    databaseUrl: String
  ) extends Actor with ActorLogging {

    // 子 actor 出现异常时如何处理子 actor
    // OneForOneStrategy 表示针对单个 actor 可以使用不同策略
    override def supervisorStrategy = OneForOneStrategy() {
      case _: CorruptedFileException => Resume
      case _: DbBrokenConnectionException => Restart
      case _: DiskError => Stop
    }

    // 将 [source] => [FileWatcher]
    var fileWatchers = sources.map { source =>
      val dbWriter = context.actorOf(
        DbWriter.props(databaseUrl), 
        DbWriter.name(databaseUrl)
      )      

      val logProcessor = context.actorOf(
        LogProcessor.props(dbWriter), 
        LogProcessor.name
      )   

      val fileWatcher = context.actorOf(
        FileWatcher.props(source, logProcessor),
        FileWatcher.name
      )

      // 注意上边虽然创建了多个其他类型的actor,但是只监视 actor fileWatcher 
      context.watch(fileWatcher)

      // 这个才是 map 的映射结果。这之前的语句只是为了执行
      fileWatcher
    }

    // 收到 子 actor 终止的消息后的处理逻辑
    def receive = {
      case Terminated(actorRef) =>
        if(fileWatchers.contains(actorRef)) {
          // 除了 被终止的 actor 外，若没有其他元素，则终止此context,即终止本 actor
          fileWatchers = fileWatchers.filterNot(_ == actorRef)
          if (fileWatchers.isEmpty) {
            log.info("Shutting down, all file watchers have failed.")
            context.system.terminate()
          }
        }
    }
  }
  

  /**
   * 文件处理
   */
  object FileWatcher {
   def props(source: String, logProcessor: ActorRef) = 
     Props(new FileWatcher(source, logProcessor))
   def name = s"file-watcher-${UUID.randomUUID.toString}"
   case class NewFile(file: File, timeAdded: Long)
   case class SourceAbandoned(uri: String)
  }

  class FileWatcher(source: String,
                    logProcessor: ActorRef)
    extends Actor with FileWatchingAbilities {
    register(source) // 注意这个函数继承FileWatchingAbilities中的默认实现
    
    import FileWatcher._

    // 收到新文件就传给 actor-logProcessor 
    // 收到资源耗尽的消息 就自毙
    def receive = {
      case NewFile(file, _) =>
        logProcessor ! LogProcessor.LogFile(file)
      case SourceAbandoned(uri) if uri == source =>
        self ! PoisonPill
    }
  }
  

  /**
   * 日志处理
   */
  object LogProcessor {
    def props(dbWriter: ActorRef) = 
      Props(new LogProcessor(dbWriter))
    def name = s"log_processor_${UUID.randomUUID.toString}"
    // represents a new log file
    case class LogFile(file: File)
  }

  class LogProcessor(dbWriter: ActorRef)
    extends Actor with ActorLogging with LogParsing {

    import LogProcessor._

    // 收到文件后，将文件解析成不同行，再将行数据 传给 actor-dbWriter
    def receive = {
      case LogFile(file) =>
        val lines: Vector[DbWriter.Line] = parse(file) // 注意此方法继承自 LogParsing 接口
        lines.foreach(dbWriter ! _)
    }
  }


  /**
   * 数据库读写
   */
  object DbWriter  {
    def props(databaseUrl: String) =
      Props(new DbWriter(databaseUrl))
    def name(databaseUrl: String) =
      s"""db-writer-${databaseUrl.split("/").last}"""

    // A line in the log file parsed by the LogProcessor Actor
    case class Line(time: Long, message: String, messageType: String)
  }

  class DbWriter(databaseUrl: String) extends Actor {
    val connection = new DbCon(databaseUrl)

    import DbWriter._

    // 将收到的 line 写入数据库
    def receive = {
      case Line(time, message, messageType) =>
        connection.write(Map('time -> time,
          'message -> message,
          'messageType -> messageType))
    }

    // 自毙前关闭数据库连接
    override def postStop(): Unit = {
      connection.close() 
    }
  }


  /**
   * 数据库连接类
   */
  class DbCon(url: String) {
    /**
     * Writes a map to a database.
     * @param map the map to write to the database.
     * @throws DbBrokenConnectionException when the connection is broken. It might be back later
     * @throws DbNodeDownException when the database Node has been removed from the database cluster. It will never work again.
     */
    def write(map: Map[Symbol, Any]): Unit =  {
      //
    }
    
    def close(): Unit = {
      //
    }
  }
  

  /**
   * 相关异常类
   */
  @SerialVersionUID(1L)
  class DiskError(msg: String)
    extends Error(msg) with Serializable

  @SerialVersionUID(1L)
  class CorruptedFileException(msg: String, val file: File)
    extends Exception(msg) with Serializable

  @SerialVersionUID(1L)
  class DbBrokenConnectionException(msg: String)
    extends Exception(msg) with Serializable

  @SerialVersionUID(1L)
  class DbNodeDownException(msg: String)
    extends Exception(msg) with Serializable


  /**
   * 相关接口
   */
  trait LogParsing {
    import DbWriter._
    // Parses log files. creates line objects from the lines in the log file.
    // If the file is corrupt a CorruptedFileException is thrown
    def parse(file: File): Vector[Line] = {
      // implement parser here, now just return dummy value
      Vector.empty[Line]
    }
  }

  trait FileWatchingAbilities {
    def register(uri: String): Unit = {

    }
  }
}
