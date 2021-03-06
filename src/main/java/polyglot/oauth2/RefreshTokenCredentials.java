package polyglot.oauth2;

import java.io.IOException;
import java.time.Clock;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.oauth2.RefreshTokenRequest;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2Credentials;

/**
 * Represents a refresh token in a specific oauth2 ecosystem. Swaps the refresh token for an access
 * token if the access token expires.
 */
public class RefreshTokenCredentials extends OAuth2Credentials {
  private static final Logger logger = LoggerFactory.getLogger(RefreshTokenCredentials.class);

  /**
   * A factor applied to the access token lifetime to make sure we refresh the token a bit earlier
   * than it actually expires.
   */
  private static final double ACCESS_TOKEN_EXPIRY_MARGIN = 0.8;

  private final String refreshTokenSecret;
  private final OauthConfig oauthConfig;
  private final Clock clock;

  public RefreshTokenCredentials(String refreshTokenSecret, OauthConfig oauthConfig, Clock clock) {
    this.refreshTokenSecret = refreshTokenSecret;
    this.oauthConfig = oauthConfig;
    this.clock = clock;
  }

  @Override
  public AccessToken refreshAccessToken() throws IOException {
    RefreshTokenRequest refreshRequest = new RefreshTokenRequest(
        new NetHttpTransport(),
        new JacksonFactory(),
        new GenericUrl(oauthConfig.getTokenEndpoint()),
        refreshTokenSecret);
    logger.info("Exchanging refresh token for access token");
    refreshRequest.setClientAuthentication(
        new BasicAuthentication(oauthConfig.getClientName(), oauthConfig.getSecret()));
    TokenResponse refreshResponse = refreshRequest.execute();

    logger.info("Success, got access token");
    return new AccessToken(
        refreshResponse.getAccessToken(),
        computeExpirtyDate(refreshResponse.getExpiresInSeconds()));
  }

  public Date computeExpirtyDate(long expiresInSeconds) {
    long expiresInSecondsWithMargin = (long) (expiresInSeconds * ACCESS_TOKEN_EXPIRY_MARGIN);
    return Date.from(clock.instant().plusSeconds(expiresInSecondsWithMargin));
  }
}
