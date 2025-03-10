package aia.faulttolerance

import java.io.File
import java.util.UUID
import akka.actor._
import akka.actor.SupervisorStrategy.{ Stop, Resume, Restart, Escalate }
import akka.actor.OneForOneStrategy
import scala.concurrent.duration._
import language.postfixOps

/**
 * A -> B -> C -> D
 * A创建B,B创建C,C创建D，父actor监视子actor，并分别负责他们的异常流程的处理
 */
package dbstrategy2 {

  object LogProcessingApp extends App {
    val sources = Vector("file:///source1/", "file:///source2/")
    val system = ActorSystem("logprocessing")

    val databaseUrls = Vector(
      "http://mydatabase1", 
      "http://mydatabase2",
      "http://mydatabase3"
    )
    
    system.actorOf(
      LogProcessingSupervisor.props(sources, databaseUrls), 
      LogProcessingSupervisor.name
    )
  }



  object LogProcessingSupervisor {
    def props(sources: Vector[String], databaseUrls: Vector[String]) =
      Props(new LogProcessingSupervisor(sources, databaseUrls))
    def name = "file-watcher-supervisor" 
  }

  class LogProcessingSupervisor(
    sources: Vector[String], 
    databaseUrls: Vector[String]
  ) extends Actor with ActorLogging {

    // 注意不同于dbstrategy1 的做法，这里没有创建多个其他类型的子 actor
    // 而是只创建了 fileWatcher
    var fileWatchers: Vector[ActorRef] = sources.map { source =>
      val fileWatcher = context.actorOf(
        Props(new FileWatcher(source, databaseUrls))
      )
      context.watch(fileWatcher)
      fileWatcher
    }

    // 如果遇到文件错误，则关闭所有子 actor
    // AllForOneStrategy 表示所有子 actor 都适用的策略
    override def supervisorStrategy = AllForOneStrategy() {
      case _: DiskError => Stop
    }

    def receive = {
      case Terminated(fileWatcher) =>
        fileWatchers = fileWatchers.filterNot(_ == fileWatcher)
        if (fileWatchers.isEmpty) {
          log.info("Shutting down, all file watchers have failed.")
          context.system.terminate()
        }
    }
  }
  


  object FileWatcher {
   case class NewFile(file: File, timeAdded: Long)
   case class SourceAbandoned(uri: String)
  }

  class FileWatcher(source: String,
                    databaseUrls: Vector[String])
    extends Actor with ActorLogging with FileWatchingAbilities {
    register(source)
    
    // 对于文件错误，选择忽略
    override def supervisorStrategy = OneForOneStrategy() {
      case _: CorruptedFileException => Resume
    }
    
    // 创建了 子actor logProcessor ,并监视
    val logProcessor = context.actorOf(
      LogProcessor.props(databaseUrls), 
      LogProcessor.name
    )   
    context.watch(logProcessor)

    import FileWatcher._

    def receive = {
      case NewFile(file, _) =>
        logProcessor ! LogProcessor.LogFile(file)
      case SourceAbandoned(uri) if uri == source =>
        log.info(s"$uri abandoned, stopping file watcher.")
        self ! PoisonPill
      case Terminated(`logProcessor`) => 
        log.info(s"Log processor terminated, stopping file watcher.")
        self ! PoisonPill
    }
  }
  


  object LogProcessor {
    def props(databaseUrls: Vector[String]) = 
      Props(new LogProcessor(databaseUrls))
    def name = s"log_processor_${UUID.randomUUID.toString}"
    // represents a new log file
    case class LogFile(file: File)
  }

  class LogProcessor(databaseUrls: Vector[String])
    extends Actor with ActorLogging with LogParsing {
    require(databaseUrls.nonEmpty)

    val initialDatabaseUrl = databaseUrls.head
    var alternateDatabases = databaseUrls.tail

    // 如果子actor数据库连接错误，则选择重启子actor
    // 如果是子actor物理节点失效，则停止子actor
    override def supervisorStrategy = OneForOneStrategy() {
      case _: DbBrokenConnectionException => Restart
      case _: DbNodeDownException => Stop
    }

    // 创建一个子actor dbWriter，并监视
    var dbWriter = context.actorOf(
      DbWriter.props(initialDatabaseUrl), 
      DbWriter.name(initialDatabaseUrl)
    )
    context.watch(dbWriter)

    import LogProcessor._

    // 收到一个文件的时候解析成行，再传给actor-dbWriter
    // 收到结束信息时，如果可选的数据库资源不为空，则另选资源重新创建一个子actor,否则自毙
    def receive = {
      case LogFile(file) =>
        val lines: Vector[DbWriter.Line] = parse(file)
        lines.foreach(dbWriter ! _)
      case Terminated(_) => 
        if(alternateDatabases.nonEmpty) {
          val newDatabaseUrl = alternateDatabases.head  
          alternateDatabases = alternateDatabases.tail    
          dbWriter = context.actorOf(
            DbWriter.props(newDatabaseUrl), 
            DbWriter.name(newDatabaseUrl)
          )      
          context.watch(dbWriter)
        } else {
          log.error("All Db nodes broken, stopping.")
          self ! PoisonPill
        }
    }
  }



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
    def receive = {
      case Line(time, message, messageType) =>
        connection.write(Map('time -> time,
          'message -> message,
          'messageType -> messageType))
    }

    override def postStop(): Unit = {
      connection.close() 
    }
  }



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
