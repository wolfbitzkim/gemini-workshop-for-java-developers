package gemini.workshop;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class LlamaStreamExample {
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

    // OpenAPI 엔드포인트를 위한 요청 본문 구성
    JsonObject message = new JsonObject();
    message.addProperty("role", "user");
    message.addProperty("content", "Summer travel plan to Paris");

    JsonArray messagesArray = new JsonArray();
    messagesArray.add(message);

    JsonObject requestBody = new JsonObject();
    requestBody.addProperty("model", "meta/llama-4-maverick-17b-128e-instruct-maas");
    requestBody.addProperty("stream", true);
    requestBody.add("messages", messagesArray);

    // HTTP 클라이언트 생성 - 타임아웃 설정 추가
    OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build();

    String endpoint = "us-east5-aiplatform.googleapis.com";
    String region = "us-east5";
    String projectId = "pjt-dev-lgcaip-playground";

    String url = String.format(
        "https://%s/v1/projects/%s/locations/%s/endpoints/openapi/chat/completions",
        endpoint, projectId, region);

    Request request = new Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer " + accessToken)
        .addHeader("Content-Type", "application/json")
        .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
        .build();

    // 디버깅을 위해 요청 정보 출력
    System.out.println("URL: " + url);
    System.out.println("Request Body: " + requestBody.toString());
    System.out.println("Sending request to Llama API...");

    try {
      Response response = client.newCall(request).execute();

      if (!response.isSuccessful()) {
        System.err.println("API 호출 실패: " + response.code() + " - " + response.message());
        System.err.println(response.body().string());
      } else {
        System.out.println("Llama 스트리밍 응답:");

        // 스트리밍 응답 처리
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
        String line;

        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) {
            continue;
          }

          // SSE (Server-Sent Events) 형식 처리
          if (line.startsWith("data: ")) {
            String data = line.substring(6);

            // [DONE] 메시지 확인
            if (data.equals("[DONE]")) {
              System.out.println("\n스트리밍 완료");
              break;
            }

            // JSON 데이터 출력
            System.out.println("Received: " + data);
          }
        }

        reader.close();
      }

      response.close();

    } catch (IOException e) {
      System.err.println("API 호출 중 오류 발생: " + e.getMessage());
      e.printStackTrace();
    }
  }
}