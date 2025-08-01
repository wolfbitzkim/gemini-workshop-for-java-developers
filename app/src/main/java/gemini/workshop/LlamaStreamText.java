package gemini.workshop;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class LlamaStreamText {
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
    System.out.println("\n=== Llama 응답 ===\n");

    try {
      Response response = client.newCall(request).execute();

      if (!response.isSuccessful()) {
        System.err.println("API 호출 실패: " + response.code() + " - " + response.message());
        System.err.println(response.body().string());
      } else {
        // 스트리밍 응답 처리
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
        String line;
        StringBuilder fullResponse = new StringBuilder();

        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) {
            continue;
          }

          // SSE (Server-Sent Events) 형식 처리
          if (line.startsWith("data: ")) {
            String data = line.substring(6);

            // [DONE] 메시지 확인
            if (data.equals("[DONE]")) {
              System.out.println("\n\n=== 스트리밍 완료 ===");
              break;
            }

            try {
              // JSON 파싱하여 content 추출
              JsonObject jsonResponse = JsonParser.parseString(data).getAsJsonObject();
              JsonArray choices = jsonResponse.getAsJsonArray("choices");

              if (choices != null && choices.size() > 0) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject delta = choice.getAsJsonObject("delta");

                if (delta != null && delta.has("content")) {
                  String content = delta.get("content").getAsString();
                  System.out.print(content);
                  fullResponse.append(content);
                }

                // finish_reason이 있으면 사용량 정보 출력
                if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                  if (jsonResponse.has("usage")) {
                    JsonObject usage = jsonResponse.getAsJsonObject("usage");
                    System.out.println("\n\n=== 토큰 사용량 ===");
                    System.out.println("프롬프트 토큰: " + usage.get("prompt_tokens").getAsInt());
                    System.out.println("완성 토큰: " + usage.get("completion_tokens").getAsInt());
                    System.out.println("총 토큰: " + usage.get("total_tokens").getAsInt());
                  }
                }
              }
            } catch (Exception e) {
              System.err.println("\nJSON 파싱 오류: " + e.getMessage());
              System.err.println("원본 데이터: " + data);
            }
          }
        }

        reader.close();

        // 전체 응답 저장 (필요한 경우)
        // System.out.println("\n\n=== 전체 응답 ===\n" + fullResponse.toString());
      }

      response.close();

    } catch (IOException e) {
      System.err.println("API 호출 중 오류 발생: " + e.getMessage());
      e.printStackTrace();
    }
  }
}