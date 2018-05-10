package org.tensorflow.demo.api;

import org.tensorflow.demo.Classifier;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

/**
 * Created by viet on 31/01/2018.
 */

public interface TensorflowApi {
    @POST("/tensorflow")
    Call<List<Classifier.Recognition>> getRecognition(@Body String image);
    @Multipart
    @POST("/tensorflow")
    Call<List<Classifier.Recognition>> getRecognition(@Part MultipartBody.Part body);
}
