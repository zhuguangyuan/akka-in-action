package com.goticks

import scala.concurrent.Future
import com.github.nscala_time.time.Imports._
import scala.util.control.NonFatal
//what about timeout? or at least termination condition?
// future -> actors scheduling time
trait TicketInfoService extends WebServiceCalls {
  import scala.concurrent.ExecutionContext.Implicits.global



  type Recovery[T] = PartialFunction[Throwable,T]

  // recover with None
  def withNone[T]: Recovery[Option[T]] = { case NonFatal(e) => None }

  // recover with empty sequence
  def withEmptySeq[T]: Recovery[Seq[T]] = { case NonFatal(e) => Seq() }

  // recover with the ticketInfo that was built in the previous step
  def withPrevious(previous: TicketInfo): Recovery[TicketInfo] = {
    case NonFatal(e) => previous
  }




  def getTicketInfo(ticketNr: String, location: Location): Future[TicketInfo] = {
    val emptyTicketInfo = TicketInfo(ticketNr, location)
    val eventInfo = getEvent(ticketNr, location).recover(withPrevious(emptyTicketInfo))

    eventInfo.flatMap { info =>
      // 包含天气信息的 ticketInfo
      val infoWithWeather = getWeather(info)
      // 包含出行信息的 ticketInfo
      val infoWithTravelAdvice = info.event.map { event =>
        getTravelAdvice(info, event)
      }.getOrElse(eventInfo)

      // 天气信息和出行信息构成 基本信息
      val ticketInfos = Seq(infoWithTravelAdvice, infoWithWeather)
      // 注意这步操作
      // 将两个都是包含部分信息的future进行fold,得到一个整合了两部分信息的future
      val infoWithTravelAndWeather: Future[TicketInfo] = Future.fold(ticketInfos/*future*/)(info/*zero*/) { 
        (acc/*返回值,此处为参数info*/, elem/*输入值，此处为参数ticketInfos里的future*/) =>
          val (travelAdvice, weather) = (elem.travelAdvice, elem.weather)
          acc.copy(
                  travelAdvice = travelAdvice.orElse(acc.travelAdvice),
                  weather = weather.orElse(acc.weather)
                )
      }

      // 相同地方的艺术家的行程信息，用于推荐及建议
      val suggestedEvents = info.event.map { event =>
        getSuggestions(event)
      }.getOrElse(Future.successful(Seq()))
      // 所有信息整合成最终的 ticketInfo
      for(info <- infoWithTravelAndWeather;
        suggestions <- suggestedEvents
      ) yield info.copy(suggestions = suggestions)
    }
  }

  def getTraffic(ticketInfo: TicketInfo): Future[TicketInfo] = {
    ticketInfo.event.map { event =>
      // // 建议重构代码
      // val futureRoute = callTrafficService(ticketInfo.userLocation, event.location, event.time)
      // futureRoute.map {
      //   routeResponse => 
      //     val travelAdvice = TravelAdvice(routeByCar = routeResponse)// 注意travelAdvice有两个参数，此处只提供一个，另外一个用默认
      //     ticketInfo.copy(travelAdvice = Some(travelAdvice))
      // }
      // 原来的代码
      callTrafficService(ticketInfo.userLocation, event.location, event.time).map{ routeResponse =>
        ticketInfo.copy(travelAdvice = Some(TravelAdvice(routeByCar = routeResponse)))
      }
    }.getOrElse(Future.successful(ticketInfo))
  }

  def getCarRoute(ticketInfo: TicketInfo): Future[TicketInfo] = {
    ticketInfo.event.map { event =>
      // // 建议重构代码
      // val futureRoute = callTrafficService(ticketInfo.userLocation, event.location, event.time)
      // futureRoute.map {
      //   routeResponse => 
      //     val newTravelAdvice = ticketInfo.travelAdvice.map(_.copy(routeByCar = routeResponse))
      //     ticketInfo.copy(travelAdvice = newTravelAdvice)
      // }.recover(withPrevious(ticketInfo))
      // 原来的代码
      callTrafficService(ticketInfo.userLocation, event.location, event.time).map{ routeResponse =>
        val newTravelAdvice = ticketInfo.travelAdvice.map(_.copy(routeByCar = routeResponse))
        ticketInfo.copy(travelAdvice = newTravelAdvice)
      }.recover(withPrevious(ticketInfo))
    }.getOrElse(Future.successful(ticketInfo))
  }

  def getPublicTransportAdvice(ticketInfo: TicketInfo): Future[TicketInfo] = {
    ticketInfo.event.map { event =>
      // // 建议重构代码
      // val futurePublicTransport = callPublicTransportService(ticketInfo.userLocation, event.location, event.time)
      // futurePublicTransport.map {
      //   publicTransportResponse =>
      //     val newTravelAdvice = ticketInfo.travelAdvice.map(_.copy(publicTransportAdvice = publicTransportResponse))
      //     ticketInfo.copy(travelAdvice = newTravelAdvice)
      // }.recover(withPrevious(ticketInfo))
      // 原来的代码
      callPublicTransportService(ticketInfo.userLocation, event.location, event.time).map{ publicTransportResponse =>
        val newTravelAdvice = ticketInfo.travelAdvice.map(_.copy(publicTransportAdvice = publicTransportResponse))
        ticketInfo.copy(travelAdvice = newTravelAdvice)
      }.recover(withPrevious(ticketInfo))
    }.getOrElse(Future.successful(ticketInfo))
  }

  def getTravelAdvice(info: TicketInfo, event: Event): Future[TicketInfo] = {

    val futureRoute = callTrafficService(info.userLocation, event.location, event.time).recover(withNone)

    val futurePublicTransport = callPublicTransportService(info.userLocation, event.location, event.time).recover(withNone)

    // 假设futureRoute=(1,2,3),futurePublicTransport=(4,5,6)
    // zip操作之后得((1,4)(2,5)(3,6)),做map映射之后就是(trav(1,4),trav(2,5),trav(3,6))
    // 注意这种形式的映射过于局限，若我要(1,5)的路线呢？
    // 且此处的map{case => copy} 最终只有一个travelAdvice???
    // $todo
    futureRoute.zip(futurePublicTransport).map { case(routeByCar, publicTransportAdvice) =>
      val travelAdvice = TravelAdvice(routeByCar, publicTransportAdvice)
      info.copy(travelAdvice = Some(travelAdvice))
    }
  }

  def getWeather(ticketInfo: TicketInfo): Future[TicketInfo] = {
    val futureWeatherX = callWeatherXService(ticketInfo).recover(withNone)
    val futureWeatherY = callWeatherYService(ticketInfo).recover(withNone)
    // 返回的两个Future[Option[Weather]],取最先返回结果的future
    // 然后将返回的Option[Weather] 结果复制给ticketInfo的weather属性
    Future.firstCompletedOf(Seq(futureWeatherX, futureWeatherY)).map { weatherResponse =>
      ticketInfo.copy(weather = weatherResponse)
    }
  }


  def getPlannedEventsWithTraverse(event: Event, artists: Seq[Artist]): Future[Seq[Event]] = {
    // 遍历 artists[] 并生成相应的Future[]
    Future.traverse(artists) { artist=>
      callArtistCalendarService(artist, event.location)
    }
  }

  def getPlannedEvents(event: Event, artists: Seq[Artist]): Future[Seq[Event]] = {
    // artists 为 Seq[Artist]形式，map成 Seq[Future[Event]]
    val events = artists.map(artist=> callArtistCalendarService(artist, event.location))
    // 将 Seq[Future[Event]] 转换成 Future[Seq[Event]] 形式
    Future.sequence(events)
  }

  def getSuggestions(event: Event): Future[Seq[Event]] = {
    // 这里返回或者 Future[Seq[artist]],或者返回Seq[]
    // 为何下文的for 还要一个recover?? 
    val futureArtists = callSimilarArtistsService(event).recover(withEmptySeq)

    for(artists <- futureArtists.recover(withEmptySeq); // 此处为何还要一个recover?
        events <- getPlannedEvents(event, artists).recover(withEmptySeq) // 调用的方法中future可能抛出异常，所以recover
    ) yield events
  }

  def getSuggestionsWithFlatMapAndMap(event: Event): Future[Seq[Event]] = {
    val futureArtists = callSimilarArtistsService(event).recover(withEmptySeq)
    // Future[Seq[Artist]] => Future[Seq[Event]]
    futureArtists.flatMap { artists=>
      // Seq[artist] => Seq[Future[Event]],因为 artist => Future[Event]
      Future.traverse(artists)(artist=> callArtistCalendarService(artist, event.location))
    }.recover(withEmptySeq)
  }

  def getTravelAdviceUsingForComprehension(info: TicketInfo, event: Event): Future[TicketInfo] = {
    val futureRoute = callTrafficService(info.userLocation, event.location, event.time).recover(withNone)
    val futurePublicTransport = callPublicTransportService(info.userLocation, event.location, event.time).recover(withNone)
    for(
        (routeByCar, publicTransportAdvice) <- futureRoute.zip(futurePublicTransport);
        travelAdvice = TravelAdvice(routeByCar, publicTransportAdvice)
    ) yield info.copy(travelAdvice = Some(travelAdvice))
  }
}

/**
 * 定义所有的服务
 */
trait WebServiceCalls {
  def getEvent(ticketNr: String, location: Location): Future[TicketInfo]

  def callWeatherXService(ticketInfo: TicketInfo): Future[Option[Weather]]

  def callWeatherYService(ticketInfo: TicketInfo): Future[Option[Weather]]

  def callTrafficService(origin: Location, destination: Location, time: DateTime): Future[Option[RouteByCar]]

  def callPublicTransportService(origin: Location, destination: Location, time: DateTime): Future[Option[PublicTransportAdvice]]

  def callSimilarArtistsService(event: Event): Future[Seq[Artist]]

  def callArtistCalendarService(artist: Artist, nearLocation: Location): Future[Event]
}
