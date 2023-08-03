package com.rohan.android.java.app.texttoimage.snapify;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_CODE = 101;
    Button savebtn;
    MaterialButton generateBtn;
    EditText userInput;
    ProgressBar prgbr;
    ImageView imgvw;

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    OkHttpClient client = new OkHttpClient();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        savebtn = findViewById(R.id.save);
        generateBtn = findViewById(R.id.generate);
        userInput = findViewById(R.id.userInput);
        prgbr = findViewById(R.id.prgsbar);
        imgvw = findViewById(R.id.imgvw);

        generateBtn.setOnClickListener(view -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(generateBtn.getWindowToken(), 0);
            String userInp = userInput.getText().toString().trim();
            if(userInp.isEmpty()){
                userInput.requestFocus();
                Toast.makeText(MainActivity.this, "Prompt Cannot be Empty", Toast.LENGTH_SHORT).show();
                return;
            }
            prgbr.setVisibility(View.VISIBLE);
            callAPI(userInp);
        });

        savebtn.setOnClickListener(view -> {
            if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED){
                saveImg();
            }else{
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_CODE);
            }
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(REQ_CODE == requestCode){
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                saveImg();
            }else{
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Permission Required", Toast.LENGTH_SHORT).show());
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void saveImg() {
        Uri images;
        ContentResolver contentResolver = getContentResolver();
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
            images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        }else {
            images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, "Rohan_Snapify_"+System.currentTimeMillis()+".jpg");
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "images/*");
        Uri uri = contentResolver.insert(images, contentValues);
        BitmapDrawable bitmapDrawable = (BitmapDrawable) imgvw.getDrawable();
        Bitmap bitmap = bitmapDrawable.getBitmap();
        try {
            OutputStream outputStream = contentResolver.openOutputStream(Objects.requireNonNull(uri));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            Objects.requireNonNull(outputStream);
            Toast.makeText(this, "Image Saved to Gallery", Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "Image Not Saved", Toast.LENGTH_SHORT).show();
        }
    }

    private void callAPI(String text) {
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("prompt", text);
            jsonBody.put("size", "512x512");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        RequestBody requestBody = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder().url("https://api.openai.com/v1/images/generations")
                .header("Authorization", "Bearer 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")
                .post(requestBody).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to Generate", Toast.LENGTH_LONG).show());
            }
@Override
public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
    String responseBody = response.body().string();
    Log.d("Snapify", "API Response: " + responseBody);
    try {
        JSONObject jsonObject = new JSONObject(responseBody);
        if (jsonObject.has("data")) {
            JSONArray dataArray = jsonObject.getJSONArray("data");
            if (dataArray.length() > 0) {
                String imageUrl = dataArray.getJSONObject(0).getString("url");
                loadImage(imageUrl);
            }
        }else if (jsonObject.has("error")) {
            JSONObject errorObject = jsonObject.getJSONObject("error");
            String errorMessage = errorObject.getString("message");
            Log.e("Snapify", "API Error: " + errorMessage);
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Explicit Content Warning")
                        .setMessage(errorMessage)
                        .setIcon(R.drawable.baseline_warning_24)
                        .setPositiveButton("OK", null)
                        .create()
                        .show();
            });
        }
    } catch (JSONException e) {
        throw new RuntimeException(e);
    }
}
        });
    }
    private void loadImage(String imageUrl) {
        runOnUiThread(() -> {
            Picasso.get().load(imageUrl).into(imgvw);
            prgbr.setVisibility(View.GONE);
        });
    }
}