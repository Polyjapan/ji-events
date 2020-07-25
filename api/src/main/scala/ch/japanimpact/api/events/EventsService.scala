package ch.japanimpact.api.events

import ch.japanimpact.api.APIError
import ch.japanimpact.api.events.events.{Event, SimpleEvent, Visibility}
import ch.japanimpact.auth.api.apitokens.{APITokensService, AppTokenRequest}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


/**
 * @author Louis Vialar
 */
@Singleton
class EventsService @Inject()(ws: WSClient, config: Configuration, tokens: APITokensService)(implicit ec: ExecutionContext) {
  private val apiBase = config.get[String]("events.baseUrl")

  private val cachedMainstream = new CachedValue[Event](this.getCurrentMainstreamEvent)
  private val cachedEvents = new CachedValue[Iterable[Event]](this.getEvents())

  private class TokenHolder {
    var token: String = _
    var exp: Long = _

    def apply(): Future[String] = {
      if (token != null && exp > System.currentTimeMillis() + 1000) Future.successful(token)
      else {
        tokens.getToken(AppTokenRequest(Set("events/*"), Set("events"), 48.hours.toSeconds))
          .map {
            case Right(token) =>
              this.token = token.token
              this.exp = System.currentTimeMillis() + token.duration * 1000 - 1000

              this.token
            case _ => throw new Exception("No token returned")
          }
      }
    }
  }

  private class CachedValue[T](refresher: => Future[Either[APIError, T]], duration: Duration = 15.minutes) {
    var cached: T = _
    var exp: Long = _

    def get: Future[T] = {
      if (cached != null && exp > System.currentTimeMillis() + 1000) Future.successful(cached)
      else {
        refresher.map {
          case Right(v) =>
            this.cached = v;
            this.exp = duration.toMillis + System.currentTimeMillis()
            this.cached
          case Left(e) =>
            println(e)

            if (cached != null) cached
            else throw new Exception("No result returned")
        }
      }
    }

    def getOpt: Future[Option[T]] =
      get.map(Some.apply).recover(_ => None)
  }


  private val token = new TokenHolder

  private def withToken[T](endpoint: String)(exec: WSRequest => Future[WSResponse])(map: JsValue => T): Future[Either[APIError, T]] =
    token()
      .map(token => ws.url(s"$apiBase/$endpoint").addHttpHeaders("Authorization" -> ("Bearer " + token)))
      .flatMap(r => mapping(r)(exec)(map))
      .recover {
        case ex: Throwable => Left(APIError(ex.getClass.getName, ex.getMessage))
      }

  private def mapping[T](request: WSRequest)(exec: WSRequest => Future[WSResponse])(map: JsValue => T): Future[Either[APIError, T]] = {
    val r = exec(request)

    r.map { response =>

      if (response.status == 200) {
        try {
          Right(map(response.json))
        } catch {
          case e: Exception =>
            e.printStackTrace()
            println(response.body)
            Left(APIError("unknown_error", "Unknown error with success response mapping"))
        }
      } else {
        try {
          Left(response.json.as[APIError])
        } catch {
          case e: Exception =>
            e.printStackTrace()
            Left(APIError("unknown_error", "Unknown error with code " + response.status))
        }
      }

    }.recover {
      case ex: Throwable => Left(APIError(ex.getClass.getName, ex.getMessage))
    }
  }

  def getEvents(visibility: Option[Visibility.Value] = None, isTest: Option[Boolean] = None, isMainstream: Option[Boolean] = None): Future[Either[APIError, Iterable[Event]]] = {
    val params = List(visibility.map(v => "visibility" -> v.toString),
      isTest.map(t => "isTest" -> t.toString),
      isMainstream.map(m => "isMainstream" -> m.toString)).flatten

    withToken(s"/events")(_
      .withQueryStringParameters(params: _*)
      .get)(_.as[Iterable[Event]])
  }

  def getCurrentEvent(visibility: Option[Visibility.Value] = None, isTest: Option[Boolean] = None, isMainstream: Option[Boolean] = None): Future[Either[APIError, Event]] = {
    val params = List(visibility.map(v => "visibility" -> v.toString),
      isTest.map(t => "isTest" -> t.toString),
      isMainstream.map(m => "isMainstream" -> m.toString)).flatten

    withToken(s"/events/current")(_
      .withQueryStringParameters(params: _*)
      .get())(_.as[Event])
  }

  def getCachedEvents: Future[Iterable[Event]] = this.cachedEvents.getOpt.map(_.getOrElse(Iterable.empty))

  def getEvent(id: Int): Future[Either[APIError, Event]] = withToken(s"/events/$id")(_.get)(_.as[Event])

  def getCurrentPublicEvent: Future[Either[APIError, Event]] =
    withToken(s"/events/current/public")(_.get)(_.as[Event])

  def getCurrentMainstreamEvent: Future[Either[APIError, Event]] =
    withToken(s"/events/current/mainstream")(_.get)(_.as[Event])

  def getCachedMainstreamEvent: Future[Option[Event]] = this.cachedMainstream.getOpt

  def createEvent(event: SimpleEvent): Future[Either[APIError, SimpleEvent]] =
    withToken("/events")(_.post(Json.toJson(event)))(_.as[SimpleEvent])

  def updateEvent(id: Int, event: SimpleEvent): Future[Either[APIError, Boolean]] =
    withToken(s"/events/$id")(_.put(Json.toJson(event)))(_ => true)

  /**
   *
   * @param id
   * @param key
   * start with `_` to make it invisible to unlogged people
   * start with `__` to make it visible only to your app
   * @param value
   */
  def setAttribute(id: Int, key: String, value: String): Future[Either[APIError, Boolean]] =
    withToken(s"/events/$id/$key")(_.post(value))(_ => true)

  def deleteAttribute(id: Int, key: String): Future[Either[APIError, Boolean]] =
    withToken(s"/events/$id/$key")(_.delete)(_ => true)

}


