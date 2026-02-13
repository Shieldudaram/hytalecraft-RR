package com.hytalecraft.pipeline;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

public class FalAPI {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String FAL_ZIMAGE_QUEUE_SUBMIT = "https://queue.fal.run/fal-ai/z-image/turbo";
    private static final String FAL_SAM3_QUEUE_SUBMIT = "https://queue.fal.run/fal-ai/sam-3/3d-objects";
    private static final String FAL_VLM_QUEUE_SUBMIT = "https://queue.fal.run/openrouter/router/vision";
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    private final String apiKey;
    private final Consumer<String> progressCallback;

    public record ModelResult(byte[] glbData, String textureUrl) {}

    public FalAPI(String apiKey, Consumer<String> progressCallback) {
        this.httpClient = HttpClient.newHttpClient();
        this.apiKey = apiKey;
        this.progressCallback = progressCallback != null ? progressCallback : msg -> {};
    }

    /**
     * Generates an image from a text prompt using Z-Image Turbo.
     */
    public String generateImageWithZImage(String prompt) throws IOException, InterruptedException {
        String augmentedPrompt = prompt + " image with plain white background, view from diagonally above";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("prompt", augmentedPrompt);
        requestBody.addProperty("image_size", "square_hd");
        requestBody.addProperty("num_inference_steps", 8);
        requestBody.addProperty("num_images", 1);
        requestBody.addProperty("enable_safety_checker", true);
        requestBody.addProperty("output_format", "png");

        progressCallback.accept("Generating image from prompt...");

        JsonObject result = submitAndPoll(FAL_ZIMAGE_QUEUE_SUBMIT, GSON.toJson(requestBody), 30, 1000);
        String imageUrl = result.getAsJsonArray("images")
                .get(0).getAsJsonObject()
                .get("url").getAsString();

        progressCallback.accept("Image generated!");
        return imageUrl;
    }

    /**
     * Converts an image to a 3D model using SAM-3D.
     */
    public ModelResult generate3DWithSam3(String imageUrl, String prompt) throws IOException, InterruptedException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("image_url", imageUrl);
        requestBody.addProperty("prompt", prompt);
        requestBody.add("point_prompts", new JsonArray());
        requestBody.add("box_prompts", new JsonArray());

        progressCallback.accept("Converting image to 3D model...");

        JsonObject result = submitAndPoll(FAL_SAM3_QUEUE_SUBMIT, GSON.toJson(requestBody), 60, 2000);
        String glbUrl = result.getAsJsonObject("model_glb")
                .get("url").getAsString();

        byte[] glbData = downloadFile(glbUrl);
        progressCallback.accept("3D model ready!");
        return new ModelResult(glbData, null);
    }

    /**
     * Uses a VLM to describe an image for better SAM-3D segmentation.
     */
    public String describeImageForSegmentation(String imageUrl, String originalPrompt) throws IOException, InterruptedException {
        String systemPrompt = "Describe the main subject in this image in simple, concrete, visual terms " +
                "for object segmentation. Focus on physical appearance, not abstract concepts. " +
                "Keep it very short (under 10 words). " +
                "Examples: 'A stone castle with towers', 'A medieval building', 'A character figure', 'A wooden house'. " +
                "Just output the description, nothing else.";

        JsonObject requestBody = new JsonObject();
        JsonArray imageUrls = new JsonArray();
        imageUrls.add(imageUrl);
        requestBody.add("image_urls", imageUrls);
        requestBody.addProperty("prompt", "What is the main subject in this image? The original request was: " + originalPrompt);
        requestBody.addProperty("system_prompt", systemPrompt);
        requestBody.addProperty("model", "google/gemini-2.5-flash");

        JsonObject result = submitAndPoll(FAL_VLM_QUEUE_SUBMIT, GSON.toJson(requestBody), 30, 1000);
        String description = result.get("output").getAsString().trim();
        description = description.replaceAll("^[\"']|[\"']$", "").trim();
        if (description.endsWith(".")) {
            description = description.substring(0, description.length() - 1);
        }

        LOGGER.atInfo().log("[FalAPI] VLM described image as: '%s'", description);
        return description;
    }

    /**
     * Fast 3D model generation: Z-Image Turbo + SAM-3D with VLM fallback.
     */
    public ModelResult generateModelFast(String prompt) throws IOException, InterruptedException {
        String imageUrl = generateImageWithZImage(prompt);

        ModelResult result;
        try {
            result = generate3DWithSam3(imageUrl, prompt);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("no masks")) {
                progressCallback.accept("Segmentation failed, analyzing image...");
                try {
                    String betterPrompt = describeImageForSegmentation(imageUrl, prompt);
                    progressCallback.accept("Retrying with: " + betterPrompt);
                    result = generate3DWithSam3(imageUrl, betterPrompt);
                } catch (IOException vlmError) {
                    progressCallback.accept("Using fallback segmentation...");
                    result = generate3DWithSam3(imageUrl, "figure");
                }
            } else {
                throw e;
            }
        }

        return result;
    }

    /**
     * Submits a request to a fal.ai queue endpoint and polls for completion.
     */
    private JsonObject submitAndPoll(String endpoint, String body, int maxAttempts, long pollIntervalMs)
            throws IOException, InterruptedException {

        HttpRequest submitRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> submitResponse = httpClient.send(submitRequest, HttpResponse.BodyHandlers.ofString());

        if (submitResponse.statusCode() != 200) {
            LOGGER.atWarning().log("[FalAPI] Queue submit error: %d - %s", submitResponse.statusCode(), submitResponse.body());
            throw new IOException("Failed to submit request: " + submitResponse.statusCode());
        }

        JsonObject submitJson = GSON.fromJson(submitResponse.body(), JsonObject.class);
        String responseUrl = submitJson.get("response_url").getAsString();
        String statusUrl = submitJson.get("status_url").getAsString();

        boolean completed = false;
        int attempts = 0;

        while (!completed && attempts < maxAttempts) {
            Thread.sleep(pollIntervalMs);
            attempts++;

            HttpRequest statusRequest = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .header("Authorization", "Key " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> statusResponse = httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString());

            if (statusResponse.statusCode() == 200 || statusResponse.statusCode() == 202) {
                JsonObject statusJson = GSON.fromJson(statusResponse.body(), JsonObject.class);
                String status = statusJson.get("status").getAsString();

                if ("COMPLETED".equals(status)) {
                    completed = true;
                } else if ("FAILED".equals(status)) {
                    throw new IOException("Request failed on server");
                }
            }
        }

        if (!completed) {
            throw new IOException("Request timed out after " + maxAttempts + " attempts");
        }

        HttpRequest resultRequest = HttpRequest.newBuilder()
                .uri(URI.create(responseUrl))
                .header("Authorization", "Key " + apiKey)
                .GET()
                .build();

        HttpResponse<String> resultResponse = httpClient.send(resultRequest, HttpResponse.BodyHandlers.ofString());

        if (resultResponse.statusCode() != 200) {
            String errorBody = resultResponse.body();
            if (errorBody != null && errorBody.contains("no masks")) {
                throw new IOException("SAM-3D segmentation failed: no masks produced");
            }
            throw new IOException("Failed to get result: " + resultResponse.statusCode());
        }

        return GSON.fromJson(resultResponse.body(), JsonObject.class);
    }

    /**
     * Downloads a file from a URL.
     */
    public byte[] downloadFile(String fileUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download file from: " + fileUrl);
        }

        return response.body();
    }
}
