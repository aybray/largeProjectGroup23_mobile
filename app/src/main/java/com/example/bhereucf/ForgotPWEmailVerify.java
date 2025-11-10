package com.example.bhereucf;

import java.util.*;


import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;
import com.postmarkapp.postmark.Postmark;
import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.data.model.message.MessageResponse;
import com.postmarkapp.postmark.client.data.model.templates.TemplatedMessage;
import com.postmarkapp.postmark.client.exception.PostmarkException;

import java.io.IOException;

// Tutorial: https://www.youtube.com/watch?v=oHmhmbnQAjM
public class ForgotPWEmailVerify {

    int code;
    int templateID;

    ForgotPWEmailVerify(int code, int templateID){
        this.code = code;
        this.templateID = templateID;
    }
    private class LongRunningTask extends AsyncTask<Void,Void,Void> {

        protected Void doInBackground(Void... voids) {

            ApiClient client;
            client = Postmark.getApiClient("API_TOKEN_HERE");

            TemplatedMessage message = new TemplatedMessage("notifications@email.ilovenarwhals.xyz", "test@blackhole.postmarkapp.com");
            message.setTemplateId(templateID);

            // set model as HashMap
            HashMap model = new HashMap<String, Object>();
            model.put("product_name", "bHere@UCF");
            model.put("code", Integer.toString(code));
            model.put("company_name", "bHere@UCF");
            model.put("company_address", "4000 Central Florida Blvd. Orlando, Florida, 32816");
            model.put("operating_system",  String.format("%s", Build.MODEL));

            message.setTemplateModel(model);
            try {
                MessageResponse response = client.deliverMessage(message);
            } catch (PostmarkException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }
    public void sendEmail() {
        new LongRunningTask().execute();
    }
}
