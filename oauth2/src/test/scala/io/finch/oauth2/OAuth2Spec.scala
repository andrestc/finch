package io.finch.oauth2

import com.twitter.finagle.httpx.Request
import com.twitter.finagle.oauth2._
import com.twitter.util.{Await, Future}
import io.finch.Endpoint.{Output, Input}
import io.finch.request.RequestReader
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, FlatSpec}
import org.mockito.Mockito._
import io.finch._

class OAuth2Spec extends FlatSpec with Matchers with Checkers with MockitoSugar {

  def runAndAwaitOutput[A](e: Endpoint[A], input: Input): Option[(Input, Output[A])] =
    e(input).map {
      case (remainder, output) => (remainder, Await.result(output()))
    }

  "The OAuth2 provider" should "authorize the requests" in {
    val at: AccessToken = mock[AccessToken]
    val dh: DataHandler[Int] = mock[DataHandler[Int]]
    val ai: AuthInfo[Int] = mock[AuthInfo[Int]]

    when(dh.findAccessToken("bar")).thenReturn(Future.value(Some(at)))
    when(dh.isAccessTokenExpired(at)).thenReturn(false)
    when(dh.findAuthInfoByAccessToken(at)).thenReturn(Future.value(Some(ai)))
    when(ai.user).thenReturn(42)

    val authInfo: RequestReader[AuthInfo[Int]] = authorize(dh)
    val e: Endpoint[Int] = get("user" ? authInfo) { ai: AuthInfo[Int] =>
      Ok(ai.user)
    }

    val i1 = Input(Request("/user", "access_token" -> "bar"))
    val i2 = Input(Request("/user"))

    runAndAwaitOutput(e, i1) shouldBe Some((i1.drop(1), Ok(42)))
    an [OAuthError] shouldBe thrownBy(runAndAwaitOutput(e, i2))
  }

  it should "issue the access token" in {
    val dh: DataHandler[Int] = mock[DataHandler[Int]]
    val at: AccessToken = mock[AccessToken]

    when(at.token).thenReturn("foobar")
    when(dh.validateClient("id", "", "password")).thenReturn(Future.value(true))
    when(dh.findUser("u", "p")).thenReturn(Future.value(Some(42)))
    when(dh.getStoredAccessToken(AuthInfo(42, "id", None, None))).thenReturn(Future.value(Some(at)))
    when(dh.isAccessTokenExpired(at)).thenReturn(false)

    val grandHandlerResult: RequestReader[GrantHandlerResult] = issueAccessToken(dh)
    val e: Endpoint[String] = get("token" ? grandHandlerResult) { ghr: GrantHandlerResult =>
      Ok(ghr.accessToken)
    }

    val i1 = Input(
      Request("/token", "grant_type" -> "password", "username" -> "u", "password" -> "p", "client_id" -> "id")
    )
    val i2 = Input(Request("/token"))

    runAndAwaitOutput(e, i1) shouldBe Some((i1.drop(1), Ok("foobar")))
    an [OAuthError] shouldBe thrownBy(runAndAwaitOutput(e, i2))
  }
}
