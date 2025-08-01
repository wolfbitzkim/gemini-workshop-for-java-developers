package gemini.workshop;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class ClaudeHaikuExample {
  public static void main(String[] args) throws IOException {
    String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
    if (credentialsPath == null) {
      System.err.println("환경변수 GOOGLE_APPLICATION_CREDENTIALS가 설정되지 않았습니다.");
      return;
    }

    GoogleCredentials credentials = GoogleCredentials
        .fromStream(new FileInputStream(credentialsPath))
        .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
    credentials.refreshIfExpired();
    String accessToken = credentials.getAccessToken().getTokenValue();

    // rawPredict 엔드포인트를 위한 요청 본문 구성
    JsonObject message = new JsonObject();
    message.addProperty("role", "user");
    message.addProperty("content", "Tell me a story about building the best SDK!");

    JsonArray messagesArray = new JsonArray();
    messagesArray.add(message);

    JsonObject requestBody = new JsonObject();
    requestBody.addProperty("anthropic_version", "vertex-2023-10-16");
    requestBody.add("messages", messagesArray);
    requestBody.addProperty("max_tokens", 1024);

    // HTTP 클라이언트 생성 - 타임아웃 설정 추가
    OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // Claude 응답을 위해 충분한 시간 설정
        .build();

    // claude-3-haiku@20240307    us-central1   ok
    // claude-3-5-haiku@20241022  us-east5      fail
    String projectId = "pjt-dev-lgcaip-playground";
    String location = "us-central1";
    String model = "claude-3-haiku@20240307";

    String url = String.format(
        "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/anthropic/models/%s:rawPredict",
        location, projectId, location, model);

    Request request = new Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer " + accessToken)
        .addHeader("Content-Type", "application/json")
        .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
        .build();

    // 디버깅을 위해 요청 정보 출력
    System.out.println("URL: " + url);
    System.out.println("Request Body: " + requestBody.toString());
    System.out.println("Sending request to Claude API...");

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        System.err.println("API 호출 실패: " + response.code() + " - " + response.message());
        System.err.println(response.body().string());
      } else {
        System.out.println("Claude 응답:");
        String responseBody = response.body().string();
        System.out.println(responseBody);
      }
    } catch (IOException e) {
      System.err.println("API 호출 중 오류 발생: " + e.getMessage());
      e.printStackTrace();
    }
  }
}