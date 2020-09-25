package com.telephone.coursetable;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.AbsoluteSizeSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.telephone.coursetable.Clock.Clock;
import com.telephone.coursetable.Database.AppDatabase;
import com.telephone.coursetable.Database.CETDao;
import com.telephone.coursetable.Database.ClassInfoDao;
import com.telephone.coursetable.Database.ExamInfoDao;
import com.telephone.coursetable.Database.GoToClassDao;
import com.telephone.coursetable.Database.GradesDao;
import com.telephone.coursetable.Database.GraduationScoreDao;
import com.telephone.coursetable.Database.PersonInfoDao;
import com.telephone.coursetable.Database.TermInfo;
import com.telephone.coursetable.Database.TermInfoDao;
import com.telephone.coursetable.Database.User;
import com.telephone.coursetable.Database.UserDao;
import com.telephone.coursetable.Fetch.WAN;
import com.telephone.coursetable.Gson.LoginResponse;
import com.telephone.coursetable.Http.HttpConnectionAndCode;
import com.telephone.coursetable.Https.Post;
import com.telephone.coursetable.Merge.Merge;
import com.telephone.coursetable.OCR.OCR;


import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Thread.sleep;

public class Login_vpn extends AppCompatActivity {

    public final static String EXTRA_USERNAME = "com.telephone.coursetable.loginvpn.username";
    public final static String EXTRA_VPN_PASSWORD = "com.telephone.coursetable.loginvpn.password.vpn";
    public final static String EXTRA_AAW_PASSWORD = "com.telephone.coursetable.loginvpn.password.aaw";
    public final static String EXTRA_SYS_PASSWORD = "com.telephone.coursetable.loginvpn.password.sys";

    private boolean updating = false;
    //private AppDatabase db = null;

    //DAOs of the database of the whole app
    private GoToClassDao gdao = null;
    private ClassInfoDao cdao = null;
    private TermInfoDao tdao = null;
    private UserDao udao = null;
    private PersonInfoDao pdao = null;
    private GraduationScoreDao gsdao = null;
    private GradesDao grdao = null;
    private ExamInfoDao edao = null;
    private CETDao cetDao = null;
    private SharedPreferences.Editor editor = MyApp.getCurrentSharedPreferenceEditor();

    private String sid = "";
    private String aaw_pwd = "";//教务处密码
    private String sys_pwd = "";//学分系统密码
    private String vpn_pwd = "";
    private String cookie = "";
    private String ck = "";

    private StringBuilder cookie_builder;
    private HttpConnectionAndCode login_res;
    private HttpConnectionAndCode outside_login_res;

    private boolean isMenuEnabled = true;

    private String title;

    private int vpn_login_fail_times = 0;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.login_vpn, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                startActivity(new Intent(Login_vpn.this, MainActivity.class));
                return true;
            case R.id.login_vpn_menu_switch_login_mode:
                startActivity(new Intent(Login_vpn.this, Login.class));
                return true;
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.login_vpn_menu_switch_login_mode);
        item.setEnabled(isMenuEnabled);
        return true;
    }

    //clear
    private void first_login() {
        setContentView(R.layout.activity_login_vpn_no_checkcode);
        setHintForEditText("上网登录页密码，默认为身份证后6位", 10, (EditText)findViewById(R.id.passwd_input));
        ((ProgressBar)findViewById(R.id.progressBar)).setVisibility(View.INVISIBLE);

        ((EditText)findViewById(R.id.passwd_input)).setInputType(((EditText)findViewById(R.id.passwd_input)).getInputType());

        Button btn_pwd = ((Button)findViewById(R.id.show_pwd));

        btn_pwd.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        ((AutoCompleteTextView)findViewById(R.id.passwd_input)).setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                        btn_pwd.setBackground(getDrawable(R.drawable.eye_open));
                        clearIMAndFocus();
                        break;
                    case MotionEvent.ACTION_UP:
                        ((AutoCompleteTextView)findViewById(R.id.passwd_input)).setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        btn_pwd.setBackground(getDrawable(R.drawable.eye_close));
                        clearIMAndFocus();
                        break;
                }
                return false;
            }
        });

        new Thread((Runnable) () -> {
            updateUserNameAutoFill();
            //if any user is activated, fill his sid and pwd in the input box
            List<User> ac_user = udao.getActivatedUser();
            if (!ac_user.isEmpty()){
                final User u = ac_user.get(0);
                runOnUiThread(() -> {

                    ((AutoCompleteTextView)findViewById(R.id.sid_input)).setText(u.username);
                    ((AutoCompleteTextView)findViewById(R.id.passwd_input)).setText(u.vpn_password);

                    aaw_pwd = u.aaw_password;
                    sys_pwd = u.password;

                    ((AutoCompleteTextView)findViewById(R.id.sid_input)).clearFocus();
                    fillStringExtra();
                });
            }else {
                runOnUiThread( ()->{
                    ((AutoCompleteTextView)findViewById(R.id.sid_input)).requestFocus();
                    fillStringExtra();
                });
            }
        }).start();
   }



    //clear
    private void system_login(String sid) {
        isMenuEnabled = true;
        invalidateOptionsMenu();

        ((ProgressBar)findViewById(R.id.progressBar)).setVisibility(View.INVISIBLE);

        setContentView(R.layout.activity_login_vpn);
        setHintForEditText("默认为身份证后6位", 10, (EditText)findViewById(R.id.aaw_pwd_input));
        ((EditText)findViewById(R.id.aaw_pwd_input)).setInputType(((EditText)findViewById(R.id.aaw_pwd_input)).getInputType());
        ((EditText)findViewById(R.id.sys_pwd_input)).setInputType(((EditText)findViewById(R.id.sys_pwd_input)).getInputType());
        ((TextView) findViewById(R.id.sid_input)).setText(sid);
        ((TextView) findViewById(R.id.sid_input)).setEnabled(false);

        ((ProgressBar)findViewById(R.id.progressBar)).setVisibility(View.INVISIBLE);

        ((TextView)findViewById(R.id.aaw_pwd_input)).setText(aaw_pwd);
        ((TextView)findViewById(R.id.sys_pwd_input)).setText(sys_pwd);

        if ( sys_pwd.isEmpty() ) {
            setFocusToEditText( (EditText) findViewById(R.id.sys_pwd_input) );
        }
        if ( aaw_pwd.isEmpty() ) {
            setFocusToEditText( (EditText) findViewById(R.id.aaw_pwd_input) );
        }

        Button btn_pwd_21 = ((Button)findViewById(R.id.show_pwd_21));

        btn_pwd_21.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        ((AutoCompleteTextView)findViewById(R.id.aaw_pwd_input)).setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                        btn_pwd_21.setBackground(getDrawable(R.drawable.eye_open));
                        clearIMAndFocus();
                        break;
                    case MotionEvent.ACTION_UP:
                        ((AutoCompleteTextView)findViewById(R.id.aaw_pwd_input)).setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        btn_pwd_21.setBackground(getDrawable(R.drawable.eye_close));
                        clearIMAndFocus();
                        break;
                }
                return false;
            }
        });


        Button btn_pwd_22 = ((Button)findViewById(R.id.show_pwd_22));

        btn_pwd_22.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        ((AutoCompleteTextView)findViewById(R.id.sys_pwd_input)).setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                        btn_pwd_22.setBackground(getDrawable(R.drawable.eye_open));
                        clearIMAndFocus();
                        break;
                    case MotionEvent.ACTION_UP:
                        ((AutoCompleteTextView)findViewById(R.id.sys_pwd_input)).setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        btn_pwd_22.setBackground(getDrawable(R.drawable.eye_close));
                        clearIMAndFocus();
                        break;
                }
                return false;
            }
        });
    }


    private void updateUserNameAutoFill(){
        final ArrayAdapter<String> ada = new ArrayAdapter<>(Login_vpn.this, android.R.layout.simple_dropdown_item_1line, udao.selectAllUserName());
        runOnUiThread(() -> {
            ((AutoCompleteTextView) findViewById(R.id.sid_input)).setAdapter(ada);
            ((AutoCompleteTextView) findViewById(R.id.sid_input)).setOnDismissListener(() -> {
                clearIMAndFocus();
                new Thread(() -> {
                    final List<User> userSelected = udao.selectUser(((AutoCompleteTextView) findViewById(R.id.sid_input)).getText().toString());
                    if (!userSelected.isEmpty()) {
                        runOnUiThread(() -> {
                            aaw_pwd = userSelected.get(0).aaw_password;
                            sys_pwd = userSelected.get(0).password;
                            ((AutoCompleteTextView) findViewById(R.id.passwd_input)).setText(userSelected.get(0).vpn_password);

                        });
                    }
                }).start();
            });
        });
    }

    /**
     * @ui
     * @clear
     */
    private void lock(){
        int[] disable_ids = {
                R.id.sid_input,
                R.id.passwd_input,
                R.id.sys_pwd_input,
                R.id.aaw_pwd_input,
                R.id.button,
                R.id.button2
        };
        int[] visible_ids = {
                R.id.progressBar
        };
        for (int id : disable_ids){
            View view = findViewById(id);
            if (view != null) {
                view.setEnabled(false);
            }
        }
        for (int id : visible_ids){
            View view = findViewById(id);
            if (view != null) {
                view.setVisibility(View.VISIBLE);
            }
        }
        isMenuEnabled = false;
        invalidateOptionsMenu();
    }

    /**
     * @ui
     * @clear
     */
    private void unlock(boolean clickable){
        int[] enable_disable_ids = {
                R.id.sid_input,
                R.id.passwd_input,
                R.id.sys_pwd_input,
                R.id.aaw_pwd_input,
                R.id.button,
                R.id.button2
        };
        int[] invisible_ids = {
                R.id.progressBar,
                R.id.login_vpn_patient
        };
        for (int id : enable_disable_ids){
            View view = findViewById(id);
            if (view != null) {
                view.setEnabled(clickable);
            }
        }
        for (int id : invisible_ids){
            View view = findViewById(id);
            if (view != null) {
                view.setVisibility(View.INVISIBLE);
            }
        }
        isMenuEnabled = clickable;
        invalidateOptionsMenu();
    }

    /**
     * @ui
     * @clear
     */
    private void try_to_show_patient(){
        View patient = findViewById(R.id.login_vpn_patient);
        View pbar = findViewById(R.id.progressBar);
        if (pbar != null) {
            patient.setVisibility(pbar.getVisibility());
        }else {
            patient.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * @ui
     * @clear
     */
    private void retry(@NonNull View snack_bar_bind_to_view, @NonNull String tip){
        Snackbar.make(snack_bar_bind_to_view, tip, BaseTransientBottomBar.LENGTH_SHORT).show();
        unlock(true);
    }

    /**
     * @ui
     * @clear
     */
    private void jump(@Nullable String tip, @NonNull Class<?> jump_to_class, @Nullable Map<String, String> string_extra){
        if (tip != null) {
            Toast.makeText(Login_vpn.this, tip, Toast.LENGTH_LONG).show();
        }
        Intent intent = new Intent(Login_vpn.this, jump_to_class);
        if (string_extra != null) {
            for (String key : string_extra.keySet()){
                intent.putExtra(key, string_extra.get(key));
            }
        }
        startActivity(intent);
    }

    /**
     * @ui
     * @clear
     */
    private void fillStringExtra(){
        Intent intent = getIntent();
        String sid = intent.getStringExtra(EXTRA_USERNAME);
        String vpn_pwd = intent.getStringExtra(EXTRA_VPN_PASSWORD);
        String sys_pwd = intent.getStringExtra(EXTRA_SYS_PASSWORD);
        String aaw_pwd = intent.getStringExtra(EXTRA_AAW_PASSWORD);
        EditText sid_input = findViewById(R.id.sid_input);
        EditText vpn_pwd_input = findViewById(R.id.passwd_input);
        EditText sys_pwd_input = findViewById(R.id.sys_pwd_input);
        EditText aaw_pwd_input = findViewById(R.id.aaw_pwd_input);
        if (sid != null) {
            if (sid_input != null) {
                sid_input.setText(sid);
            }
        }
        if (vpn_pwd != null) {
            if (vpn_pwd_input != null) {
                vpn_pwd_input.setText(vpn_pwd);
            }
        }
        if (sys_pwd != null) {
            this.sys_pwd = sys_pwd;
            if (sys_pwd_input != null) {
                sys_pwd_input.setText(sys_pwd);
            }
        }
        if (aaw_pwd != null) {
            this.aaw_pwd = aaw_pwd;
            if (aaw_pwd_input != null) {
                aaw_pwd_input.setText(aaw_pwd);
            }
        }
    }

    /**
     * @clear
     */
    private Map<String, String> getSidPasswordExtraMap(){
        return new HashMap<String, String>() {
            {
                put(EXTRA_USERNAME, sid);
                put(EXTRA_AAW_PASSWORD, aaw_pwd);
                put(EXTRA_SYS_PASSWORD, sys_pwd);
                put(EXTRA_VPN_PASSWORD, vpn_pwd);
            }
        };
    }

    /**
     * @non-ui
     * the sid and pwd must be correct
     * @return
     * - see {@link Login_vpn#vpn_login(Context, String, String)}
     * ▲ note that: if return null, it means network error, because the sid and pwd must be correct
     * @clear
     */
    private String regainVPNTicket(@NonNull String sid, @NonNull String pwd){
        int times = 0;
        String ticket = null;
        while (times <= MyApp.web_vpn_ticket_regain_times){
            ticket = vpn_login(Login_vpn.this, sid, pwd);
            times++;
            if (ticket != null){
                break;
            }
        }
        return ticket;
    }

    /**
     * @non-ui
     * the sid and vpn_pwd must be correct
     * @throws NoSuchFieldException -> means 302 when get check-code
     * @throws IllegalAccessException -> means vpn login ip forbidden
     * @return
     * - null: network error
     * - not null: success
     * @clear
     */
    private Bitmap try_to_get_check_code(@NonNull String cookie, @NonNull String sid, @NonNull String vpn_pwd) throws NoSuchFieldException, IllegalAccessException{
        Bitmap ck_bitmap = null;
        int times = 0;
        while (times <= MyApp.check_code_regain_times){
            HttpConnectionAndCode res = WAN.checkcode(Login_vpn.this, cookie);
            ck_bitmap = (Bitmap) res.obj;
            times++;
            if (ck_bitmap != null){//success
                break;
            }else if (res.resp_code == 302){//jump to other page
                throw new NoSuchFieldException();
            }else {//network error
                if (times >= MyApp.check_code_regain_times/3) {
                    if (res.c != null) {
                        res.c.disconnect();
                    }
                }
                cookie = regainVPNTicket(sid, vpn_pwd);
                if (cookie == null){//network error
                    break;
                }else if (cookie.equals(getResources().getString(R.string.wan_vpn_ip_forbidden))){//vpn login forbidden
                    throw new IllegalAccessException();
                }
            }
        }
        return ck_bitmap;
    }

    //clear
    private void setFocusToEditText(EditText et) {
        if (et != null) {
            et.requestFocus();
            if (!et.getText().toString().isEmpty()) {
                et.clearFocus();
            }
        }
    }


    //clear
    private void clearIMAndFocus() {
        EditText ets = (EditText) findViewById(R.id.sid_input);
        EditText etp = (EditText) findViewById(R.id.passwd_input);
        EditText etw = (EditText) findViewById(R.id.aaw_pwd_input);
        EditText ety = (EditText)findViewById(R.id.sys_pwd_input);


        if (ets != null) {
            ets.setEnabled(!ets.isEnabled());
            ets.setEnabled(!ets.isEnabled());
            ets.clearFocus();
        }
        if (etp != null) {
            etp.setEnabled(!etp.isEnabled());
            etp.setEnabled(!etp.isEnabled());
            etp.clearFocus();
        }
        if (etw != null) {
            etw.setEnabled(!etw.isEnabled());
            etw.setEnabled(!etw.isEnabled());
            etw.clearFocus();
        }
        if (ety != null) {
            ety.setEnabled(!ety.isEnabled());
            ety.setEnabled(!ety.isEnabled());
            ety.clearFocus();
        }

    }


    //clear
    public static void deleteOldDataFromDatabase(GoToClassDao gdao, ClassInfoDao cdao, TermInfoDao tdao, PersonInfoDao pdao, GraduationScoreDao gsdao, GradesDao grdao, ExamInfoDao edao, CETDao cetDao) {
        gdao.deleteAll();
        cdao.deleteAll();
        tdao.deleteAll();
        pdao.deleteAll();
        gsdao.deleteAll();
        grdao.deleteAll();
        edao.deleteAll();
        cetDao.deleteAll();
    }

    //clear
    public static HttpConnectionAndCode login(Context c, String sid, String pwd, String ckcode, String cookie, @Nullable StringBuilder builder) {
        final String NAME = "login()";
        Resources r = c.getResources();
        String body = "us=" + sid + "&pwd=" + pwd + "&ck=" + ckcode;
        HttpConnectionAndCode login_res = Post.post(
                "https://v.guet.edu.cn/http/77726476706e69737468656265737421f2fc4b8b69377d556a468ca88d1b203b/Login/SubmitLogin",
                null,
                r.getString(R.string.user_agent),
                r.getString(R.string.wan_vpn_login_referer),
                body,
                cookie,
                "}",
                r.getString(R.string.cookie_delimiter),
                r.getString(R.string.lan_login_success_contain_response_text),
                null,
                null
        );
        if ( login_res.code == 0 ) {
            LoginResponse response = new Gson().fromJson(login_res.comment, LoginResponse.class);
            login_res.comment = response.getMsg();
        }else if ( login_res.code == -6 ) {
            if ( login_res.comment.contains("<html") ) {
                login_res.code = -7;
            }
        }
        if (login_res.code == 0 && builder != null) {
            if (!builder.toString().isEmpty()) {
                builder.append(r.getString(R.string.cookie_delimiter));
            }
            builder.append(login_res.cookie);
        }
        Log.e(NAME, "body: " + body + " code: " + login_res.code + " resp_code: " + login_res.resp_code + " comment/msg: " + login_res.comment);

        return login_res;
    }

    //教务处登录
    public static HttpConnectionAndCode outside_login_test(Context c, final String sid, final String pwd, String cookie){
        final String NAME = "outside_login_test()";
        Resources r = c.getResources();
        String body = "username=" + sid + "&passwd=" + pwd + "&login=%B5%C7%A1%A1%C2%BC";
        Log.e(NAME + " " + "body", body);
        HttpConnectionAndCode login_res = com.telephone.coursetable.Https.Post.post(
                "https://v.guet.edu.cn/http/77726476706e69737468656265737421a1a013d2766626013051d0/student/public/login.asp",
                null,
                r.getString(R.string.user_agent),
                "https://v.guet.edu.cn/http/77726476706e69737468656265737421e5e3529f69377d556a468ca88d1b203b/",
                body,
                cookie,
                null,
                r.getString(R.string.cookie_delimiter),
                null,
                null,
                false
        );
        if ( login_res.code == 0 && login_res.resp_code == 302 && login_res.c.getHeaderFields().get("location").get(0).equals("menu.asp?menu=mnall.asp") ){
            Log.e(NAME + " " + "login status", "success");
        }else {
            if (login_res.code == 0 ){
                //200 + 2333333333
                if ( login_res.resp_code == 200 && login_res.comment != null && login_res.comment.contains("77726476706e69737468656265737421a1a013d2766626013051d0") ) {
                    //密码错误
                    login_res.code = -6;
                }
                //200 + empty
                else if ( login_res.resp_code == 200 && login_res.comment != null && login_res.comment.isEmpty() ) {
                    //未知错误
                    login_res.code = -7;
                }
                //302
                //200 + else
                else if ( login_res.resp_code == 302 || ( login_res.resp_code == 200 && login_res.comment != null ) ) {
                    //外网被禁
                    login_res.code = -8;
                }
            }
            Log.e(NAME + " " + "login status", "fail" + " code: " + login_res.code);
        }
        return login_res;
    }


    /**
     * @param pwd origin password
     * @return - String : the encrypted password
     * - null : fail
     * @ui/non-ui get encrypted password
     * @clear
     */
    public static String encrypt(String pwd) {
        int[] key = {134, 8, 187, 0, 251, 59, 238, 74, 176, 180, 24, 67, 227, 252, 205, 80};
        //for good, pwd's length should not be 0
        int pwd_len = pwd.length();
        try {
            if (pwd.length() % 16 != 0) {
                int need_num = 16 - pwd.length() % 16;
                StringBuilder pwd_builder = new StringBuilder();
                pwd_builder.append(pwd);
                for (int i = 0; i < need_num; i++) {
                    pwd_builder.append("0");
                }
                pwd = pwd_builder.toString();
            }
            byte[] pwd_bytes = pwd.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < pwd_bytes.length; i++) {
                pwd_bytes[i] ^= key[i % 16];
            }
            StringBuilder encrypt_builder = new StringBuilder();
            encrypt_builder.append("77726476706e6973617765736f6d6521");
            for (int i = 0; i < pwd_len; i++) {
                byte b = pwd_bytes[i];
                encrypt_builder.append(String.format("%02x", b));
            }
            return encrypt_builder.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @return - cookie containing ticket : success
     * - {@link R.string#wan_vpn_ip_forbidden} : fail, ip forbidden
     * - null : fail
     * @non-ui try to login GUET web vpn, if success:
     * 1. return cookie containing web vpn ticket
     * @clear
     */
    public static String vpn_login(Context c, String id, String pwd) {
        final String NAME = "vpn_login()";
        Resources r = c.getResources();
        String body = "auth_type=local&username=" + id + "&sms_code=&password=" + pwd;
        Log.e(NAME + " " + "body", body);
        if (pwd.length() <= 0) {
            Log.e(NAME, "fail");
            return null;
        }
        pwd = encrypt(pwd);
        body = "auth_type=local&username=" + id + "&sms_code=&password=" + pwd;
        Log.e(NAME + " " + "encrypted body", body);
        HttpConnectionAndCode get_ticket_res = com.telephone.coursetable.Https.Get.get(
                r.getString(R.string.wan_vpn_get_ticket_url),
                null,
                r.getString(R.string.user_agent),
                r.getString(R.string.wan_vpn_get_ticket_referer),
                null,
                null,
                r.getString(R.string.cookie_delimiter),
                null,
                null,
                null
        );
        String cookie = get_ticket_res.cookie;
        if (cookie == null || cookie.isEmpty()){
            Log.e(NAME, "fail | can not get init vpn ticket");
            return null;
        }
        cookie = cookie.substring(cookie.indexOf("wengine_vpn_ticket"));
        cookie = cookie.substring(0, cookie.indexOf(r.getString(R.string.cookie_delimiter)));
        cookie += r.getString(R.string.cookie_delimiter) + "show_vpn=1" + r.getString(R.string.cookie_delimiter) + "refresh=1";
        Log.e(NAME + " " + "ticket cookie", cookie);
        HttpConnectionAndCode try_to_login_res = com.telephone.coursetable.Https.Post.post(
                r.getString(R.string.wan_vpn_login_url),
                null,
                r.getString(R.string.user_agent),
                r.getString(R.string.wan_vpn_login_referer),
                body,
                cookie,
                "}",
                r.getString(R.string.cookie_delimiter),
                r.getString(R.string.wan_vpn_login_success_contain_response_text),
                null,
                null
        );
        if (try_to_login_res.comment != null) {
            Log.e(NAME + " " + "try to login response", try_to_login_res.comment);
        }
        if (try_to_login_res.code == 0) {
            Log.e(NAME, "success");
            return cookie;
        } else {
            if (try_to_login_res.comment != null && try_to_login_res.comment.contains(r.getString(R.string.wan_vpn_login_need_confirm_contain_response_text))) {
                Log.e(NAME + " " + "need confirm", "confirm...");
                HttpConnectionAndCode confirm_login_res = com.telephone.coursetable.Https.Post.post(
                        r.getString(R.string.wan_vpn_confirm_login_url),
                        null,
                        r.getString(R.string.user_agent),
                        r.getString(R.string.wan_vpn_confirm_login_referer),
                        null,
                        cookie,
                        null,
                        r.getString(R.string.cookie_delimiter),
                        null,
                        null,
                        null
                );
                if (confirm_login_res.code == 0) {
                    Log.e(NAME, "success");
                    return cookie;
                }
            } else if (try_to_login_res.comment != null && try_to_login_res.comment.contains(r.getString(R.string.wan_vpn_login_ip_forbidden_contain_response_text))) {
                Log.e(NAME, "fail | ip forbidden");
                return r.getString(R.string.wan_vpn_ip_forbidden);
            }
            Log.e(NAME, "fail");
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        MyApp.clearRunningActivity(this);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MyApp.setRunning_activity(MyApp.RunningActivity.LOGIN_VPN);
        MyApp.setRunning_activity_pointer(this);
        AppDatabase db = MyApp.getCurrentAppDB();

        gdao = db.goToClassDao();
        cdao = db.classInfoDao();
        tdao = db.termInfoDao();
        udao = db.userDao();
        pdao = db.personInfoDao();
        gsdao = db.graduationScoreDao();
        grdao = db.gradesDao();
        edao = db.examInfoDao();
        cetDao = db.cetDao();

        cookie_builder = new StringBuilder();

        title = getSupportActionBar().getTitle().toString();

        first_login();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    /**
     * @ui 1. call {@link #clearIMAndFocus()}
     * 2. get the username in the sid input box
     * 3. show an AlertDialog to warn user:
     * - if press yes, a new thread will be started:
     * 1. try to delete the user from the database with the username in the sid input box
     * 2. call {@link #updateUserNameAutoFill()}
     * 3. clear sid input box and password input box
     * 4. set focus to sid input box
     * 5. call
     * - if press no, nothing will happen
     * @clear
     */
    public void deleteUser(View view) {

        final String NAME = "deleteUser()";
        clearIMAndFocus();
        String sid = ((AutoCompleteTextView) findViewById(R.id.sid_input)).getText().toString();
        getAlertDialog("确定要取消记住用户" + " " + sid + " " + "的登录信息吗？",
                (DialogInterface.OnClickListener) (dialogInterface, i) -> new Thread((Runnable) () -> {
                    udao.deleteUser(sid);
                    Log.e(NAME + " " + "user deleted", sid);
                    updateUserNameAutoFill();
                    runOnUiThread((Runnable) () -> {
                        aaw_pwd = "";
                        sys_pwd = "";
                        ((AutoCompleteTextView)findViewById(R.id.sid_input)).setText("");
                        ((AutoCompleteTextView)findViewById(R.id.passwd_input)).setText("");
                        setFocusToEditText((EditText)findViewById(R.id.sid_input));
                    });
                }).start(),
                (DialogInterface.OnClickListener) (dialogInterface, i) -> {},
                null, null).show();

    }

    private AlertDialog getAlertDialog(@Nullable final String m, @NonNull DialogInterface.OnClickListener yes, @NonNull DialogInterface.OnClickListener no, @Nullable View view, @Nullable String title){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (m != null) {
            builder.setMessage(m);
        }
        builder.setPositiveButton(getResources().getString(R.string.ok_btn_text_zhcn), yes)
                .setNegativeButton(getResources().getString(R.string.deny_btn_zhcn), no);
        if (view != null){
            builder.setView(view);
        }
        if (title != null){
            builder.setTitle(title);
        }
        return builder.create();
    }

    /**
     * @return - true : everything is ok
     * - false : something went wrong
     * @non-ui 1. pull all user-related data from internet
     * 2. save the pulled data to database and shared preference
     * @clear
     */
    public static boolean fetch_merge(Context c, String cookie, PersonInfoDao pdao, TermInfoDao tdao,
                                      GoToClassDao gdao, ClassInfoDao cdao, GraduationScoreDao gsdao,
                                      GradesDao grdao, ExamInfoDao edao , CETDao cetDao, SharedPreferences.Editor editor) {
        final String NAME = "fetch_merge()";
        HttpConnectionAndCode res;
        HttpConnectionAndCode res_add;

        res = WAN.personInfo(c, cookie);
        res_add = WAN.studentInfo(c, cookie);
        if (res.code != 0 || res_add.code != 0) {
            Log.e(NAME, "fail");
            return false;
        }
        Merge.personInfo(res.comment, res_add.comment, pdao);

        res = WAN.termInfo(c, cookie);
        if (res.code != 0) {
            Log.e(NAME, "fail");
            return false;
        }
        Merge.termInfo(c, res.comment, tdao);

        List<String> terms = tdao.getTermsSince(
                pdao.getGrade().get(0) + "-" + (pdao.getGrade().get(0) + 1) + "_1"
        );
        List<TermInfo> term_list = tdao.selectAll();
        for (TermInfo term : term_list) {
            if (terms.contains(term.term)) continue;
            tdao.deleteTerm(term.term);
        }
        res = WAN.goToClass_ClassInfo(c, cookie);
        if (res.code != 0) {
            Log.e(NAME, "fail");
            return false;
        }
        Merge.goToClass_ClassInfo(res.comment, gdao, cdao);

        res = WAN.graduationScore(c, cookie);
        res_add = WAN.graduationScore2(c,cookie);
        if (res.code != 0 || res_add.code != 0) {
            Log.e(NAME, "fail");
            return false;
        }
        Merge.graduationScore(res.comment,res_add.comment,gsdao);

        res = WAN.grades(c, cookie);
        if (res.code != 0) {
            Log.e(NAME, "fail");
            return false;
        }
        Merge.grades(res.comment, grdao);

        res = WAN.examInfo(c, cookie);
        if (res.code != 0){
            Log.e(NAME, "fail");
            return false;
        }
        Merge.examInfo(res.comment, edao);

        res = WAN.cet(c, cookie);
        if (res.code != 0){
            Log.e(NAME, "fail");
            return false;
        }
        Merge.cet(res.comment, cetDao);

        res = WAN.hour(c, cookie);
        if (res.code != 0) {
            Log.e(NAME, "fail");
            return false;
        }
        Merge.hour(c, res.comment, editor);

        Log.e(NAME, "success");
        return true;
    }


    //clear
    public void login_thread_1(View view) {
        //after click button login , it will go to login_thread

        lock();
        clearIMAndFocus();

        sid = ((TextView) findViewById(R.id.sid_input)).getText().toString();
        vpn_pwd = ((TextView) findViewById(R.id.passwd_input)).getText().toString();

        new Thread(new Runnable() {
            @Override
            public void run() {

                new Thread(()->{
                    try {
                        sleep(MyApp.patient_time);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    runOnUiThread(Login_vpn.this::try_to_show_patient);
                }).start();

                //get cookie
                cookie = Login_vpn.vpn_login(Login_vpn.this, sid, vpn_pwd);

                String tip;
                //fail : password or web
                if (cookie == null) {
                    //reason
                    tip = "WebVPN验证失败";
                }
                else if(cookie.equals(getResources().getString(R.string.wan_vpn_ip_forbidden))){
                    tip = getResources().getString(R.string.wan_login_vpn_ip_forbidden_tip);
                }
                //success
                else {
                    tip = null;
                }

                final String NAME = "login_thread_1()";

                /** detect new activity || skip no activity */
                if (MyApp.getRunning_activity().equals(MyApp.RunningActivity.NULL)){
                    Log.e(NAME, "no activity is running, login = " + Login_vpn.this.toString() + " canceled");
                    runOnUiThread(()->Toast.makeText(Login_vpn.this, getResources().getString(R.string.wan_login_vpn_cancel_tip), Toast.LENGTH_SHORT).show());
                    return;
                }
                Log.e(NAME, "login activity pointer = " + Login_vpn.this.toString());
                Log.e(NAME, "running activity pointer = " + MyApp.getRunning_activity_pointer().toString());
                if (!Login_vpn.this.toString().equals(MyApp.getRunning_activity_pointer().toString())){
                    Log.e(NAME, "new running activity detected = " + MyApp.getRunning_activity_pointer().toString() + ", login = " + Login_vpn.this.toString() + " canceled");
                    runOnUiThread(()->Toast.makeText(Login_vpn.this, getResources().getString(R.string.wan_login_vpn_cancel_tip), Toast.LENGTH_SHORT).show());
                    return;
                }

                runOnUiThread(() -> {
                    if (tip != null) {
                        Snackbar.make(view, tip, BaseTransientBottomBar.LENGTH_LONG).show();
                        if (tip.equals("WebVPN验证失败")) {

                            ((ProgressBar) findViewById(R.id.progressBar)).setVisibility(View.INVISIBLE);
                            unlock(true);

                            vpn_login_fail_times++;
                            if (vpn_login_fail_times >= 3){
                                jump(getResources().getString(R.string.wan_login_vpn_relogin_tip), Login_vpn.class, getSidPasswordExtraMap());
                            }
                            return;

                        }else if(tip.equals(getResources().getString(R.string.wan_login_vpn_ip_forbidden_tip))){

                            ((ProgressBar) findViewById(R.id.progressBar)).setVisibility(View.INVISIBLE);
                            unlock(true);

                            vpn_login_fail_times++;
                            if (vpn_login_fail_times >= 3){
                                jump(getResources().getString(R.string.wan_login_vpn_relogin_tip), Login_vpn.class, getSidPasswordExtraMap());
                            }
                            return;

                        }
                    }else {
                        system_login(sid);
                    }
                });
            }
        }).start();
    }



    public void login_thread_2(View view){
        lock();
        clearIMAndFocus();

        if (getSupportActionBar().getTitle().toString().equals(getResources().getString(R.string.lan_title_login_updated_fail))){
            getSupportActionBar().setTitle(title);
        }

        aaw_pwd = ((TextView) findViewById(R.id.aaw_pwd_input)).getText().toString();
        sys_pwd = ((TextView) findViewById(R.id.sys_pwd_input)).getText().toString();

        if( aaw_pwd.isEmpty() ){
            retry(view, getResources().getString(R.string.wan_snackbar_outside_test_login_fail));
            setFocusToEditText((EditText)findViewById(R.id.aaw_pwd_input));
            return;
        }

        if( sys_pwd.isEmpty() ){
            retry(view, getResources().getString(R.string.wan_snackbar_sys_pwd_login_fail));
            setFocusToEditText((EditText)findViewById(R.id.sys_pwd_input));
            return;
        }

        new Thread(()->{

            new Thread(()->{
                try {
                    sleep(MyApp.patient_time);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                runOnUiThread(Login_vpn.this::try_to_show_patient);
            }).start();

            /** -------------------------------------------------------------------------*/
            Bitmap ck_pic;
            try {
                ck_pic = try_to_get_check_code(cookie, sid, vpn_pwd);
            } catch (NoSuchFieldException e) {
                runOnUiThread(()->jump(getResources().getString(R.string.wan_login_vpn_relogin_tip), Login_vpn.class, getSidPasswordExtraMap()));
                return;
            } catch (IllegalAccessException e) {
                runOnUiThread(()->retry(view, getResources().getString(R.string.wan_login_vpn_ip_forbidden_tip)));
                return;
            }

            if (ck_pic == null){
                runOnUiThread(()->retry(view, getResources().getString(R.string.wan_login_vpn_network_error_tip)));
                return;
            }else {
                ck = OCR.getTextFromBitmap(Login_vpn.this, ck_pic, MyApp.ocr_lang_code);
            }
            /** -------------------------------------------------------------------------*/

            login_res = login(Login_vpn.this, sid, sys_pwd, ck, cookie, null);
            outside_login_res = outside_login_test(Login_vpn.this, sid, aaw_pwd, cookie);

            if ( login_res.comment == null || outside_login_res.comment == null ) {
                runOnUiThread(()->retry(view, getResources().getString(R.string.wan_login_vpn_network_error_tip)));
                return;
            }else if( ( login_res.comment.isEmpty() ) || outside_login_res.code == -7 || login_res.code == -7) {
                runOnUiThread(()->jump(getResources().getString(R.string.wan_login_vpn_relogin_tip), Login_vpn.class, getSidPasswordExtraMap()));
                return;
            }

            if( login_res.code != 0 || outside_login_res.code != 0 ){

                int count_ck_loop = 0;
                while ( login_res.comment.contains("验证码") ) {

                    /** -------------------------------------------------------------------------*/
                    ck_pic = null;
                    try {
                        ck_pic = try_to_get_check_code(cookie, sid, vpn_pwd);
                    } catch (NoSuchFieldException e) {
                        runOnUiThread(()->jump(getResources().getString(R.string.wan_login_vpn_relogin_tip), Login_vpn.class, getSidPasswordExtraMap()));
                        return;
                    } catch (IllegalAccessException e) {
                        runOnUiThread(()->retry(view, getResources().getString(R.string.wan_login_vpn_ip_forbidden_tip)));
                        return;
                    }

                    if (ck_pic == null){
                        runOnUiThread(()->retry(view, getResources().getString(R.string.wan_login_vpn_network_error_tip)));
                        return;
                    }else {
                        ck = OCR.getTextFromBitmap(Login_vpn.this, ck_pic, MyApp.ocr_lang_code);
                    }
                    /** -------------------------------------------------------------------------*/

                    login_res = login(Login_vpn.this, sid, sys_pwd, ck, cookie, null);

                    if ( login_res.comment == null ) {
                        runOnUiThread(()->retry(view, getResources().getString(R.string.wan_login_vpn_network_error_tip)));
                        return;
                    }else if( login_res.comment.isEmpty() || login_res.code == -7) {
                        runOnUiThread(() -> jump(getResources().getString(R.string.wan_login_vpn_relogin_tip), Login_vpn.class, getSidPasswordExtraMap()));
                        return;
                    }
                    count_ck_loop++;
                    if (count_ck_loop > 6) {
                        runOnUiThread(()-> jump(getResources().getString(R.string.wan_login_vpn_relogin_tip), Login_vpn.class, getSidPasswordExtraMap()));
                        return;
                    }
                }

                runOnUiThread((Runnable)()->{

                    if(  login_res.comment.contains("密码")  ) {
                        retry(view, getResources().getString(R.string.wan_snackbar_sys_pwd_login_fail));
                        ((EditText)findViewById(R.id.sys_pwd_input)).setText("");
                        setFocusToEditText((EditText)findViewById(R.id.sys_pwd_input));
                        return;
                    }else if ( outside_login_res.code == -6 ) {
                        retry(view, getResources().getString(R.string.wan_snackbar_outside_test_login_fail));
                        ((EditText)findViewById(R.id.aaw_pwd_input)).setText("");
                        setFocusToEditText((EditText)findViewById(R.id.aaw_pwd_input));
                        return;
                    }else if ( outside_login_res.code == -8 ) {
                        jump(getResources().getString(R.string.wan_login_vpn_relogin_tip), Login_vpn.class, getSidPasswordExtraMap());
                        return;
                    }else if ( login_res.code != 0 || outside_login_res.code != 0 ){
                        retry(view, getResources().getString(R.string.wan_snackbar_unknown_fail));
                        return;
                    }

                });

            }

            if( login_res.code == 0 && outside_login_res.code == 0 ) {
                /** get shared preference and its editor */
                final SharedPreferences shared_pref = MyApp.getCurrentSharedPreference();
                final SharedPreferences.Editor editor = MyApp.getCurrentSharedPreferenceEditor();

                final String NAME = "login_thread_2()";

                /** detect new activity || skip no activity */
                if (MyApp.getRunning_activity().equals(MyApp.RunningActivity.NULL)){
                    Log.e(NAME, "no activity is running, login = " + Login_vpn.this.toString() + " canceled");
                    runOnUiThread(()->Toast.makeText(Login_vpn.this, getResources().getString(R.string.wan_login_vpn_cancel_tip), Toast.LENGTH_SHORT).show());
                    return;
                }
                Log.e(NAME, "login activity pointer = " + Login_vpn.this.toString());
                Log.e(NAME, "running activity pointer = " + MyApp.getRunning_activity_pointer().toString());
                if (!Login_vpn.this.toString().equals(MyApp.getRunning_activity_pointer().toString())){
                    Log.e(NAME, "new running activity detected = " + MyApp.getRunning_activity_pointer().toString() + ", login = " + Login_vpn.this.toString() + " canceled");
                    runOnUiThread(()->Toast.makeText(Login_vpn.this, getResources().getString(R.string.wan_login_vpn_cancel_tip), Toast.LENGTH_SHORT).show());
                    return;
                }

                /** insert/replace new user into database */
                udao.insert(new User(sid, aaw_pwd, sys_pwd, vpn_pwd));
                /** deactivate all user in database */
                udao.disableAllUser();
                /** set {@link MyApp#running_login_thread} to true */
                MyApp.setRunning_login_thread(true);
                /** show tip snack-bar, change title */
                runOnUiThread(() -> {
                    Snackbar.make(view, getResources().getString(R.string.lan_snackbar_data_updating), BaseTransientBottomBar.LENGTH_LONG).show();
                    getSupportActionBar().setTitle(getResources().getString(R.string.lan_title_login_updating));
                });

                int times = 0;
                boolean fetch_merge_res = false;
                while (times <= MyApp.web_vpn_refetch_times && !fetch_merge_res) {
                    if (times >= MyApp.web_vpn_refetch_times/3){
                        if (login_res.c != null) {
                            login_res.c.disconnect();
                        }
                    }
                    /** clear shared preference */
                    editor.clear();
                    /** commit shared preference */
                    editor.commit();
                    /** call {@link #deleteOldDataFromDatabase()} */
                    deleteOldDataFromDatabase(gdao, cdao, tdao, pdao, gsdao, grdao, edao, cetDao);
                    fetch_merge_res = fetch_merge(Login_vpn.this, cookie, pdao, tdao, gdao, cdao, gsdao, grdao, edao, cetDao, editor);
                    times++;
                }

                /** commit shared preference */
                editor.commit();

                if (fetch_merge_res) {

                    /** locate now, print the locate-result to log */
                    Log.e(
                            NAME + " " + "locate now",
                            Clock.locateNow(
                                    Clock.nowTimeStamp(), tdao, shared_pref, MyApp.times,
                                    DateTimeFormatter.ofPattern(getResources().getString(R.string.server_hours_time_format)),
                                    getResources().getString(R.string.pref_hour_start_suffix),
                                    getResources().getString(R.string.pref_hour_end_suffix),
                                    getResources().getString(R.string.pref_hour_des_suffix)
                            ) + ""
                    );

                    udao.activateUser(sid);

                    MyApp.setRunning_login_thread(false);

                    runOnUiThread(() -> {
                        unlock(false);
                        Toast.makeText(Login_vpn.this, getResources().getString(R.string.lan_toast_update_success), Toast.LENGTH_SHORT).show();
                        getSupportActionBar().setTitle(getResources().getString(R.string.lan_title_login_updated));
                        if (!MyApp.getRunning_activity().equals(MyApp.RunningActivity.NULL)){
                            Log.e(NAME, "start a new Main Activity...");
                            /** start a new {@link MainActivity} */
                            startActivity(new Intent(Login_vpn.this, MainActivity.class));
                        }else {
                            Log.e(NAME, "update success but no activity is running, NOT start new Main Activity");
                        }
                    });

                } else {
                    /** set {@link MyApp#running_login_thread} to false */
                    MyApp.setRunning_login_thread(false);
                    /** if login activity is current running activity */
                    if (MyApp.getRunning_activity().equals(MyApp.RunningActivity.LOGIN_VPN)){
                        runOnUiThread(() -> {
                            unlock(true);
                            /** show tip snack-bar, change title */
                            Snackbar.make(view, getResources().getString(R.string.lan_toast_update_fail), BaseTransientBottomBar.LENGTH_LONG).show();
                            getSupportActionBar().setTitle(getResources().getString(R.string.lan_title_login_updated_fail));
                        });
                    }else {
                        runOnUiThread(() -> {
                            /** show tip toast */
                            Toast.makeText(Login_vpn.this, getResources().getString(R.string.lan_toast_update_fail), Toast.LENGTH_SHORT).show();
                            /** if main activity is current running activity */
                            if (MyApp.getRunning_activity().equals(MyApp.RunningActivity.MAIN) && MyApp.getRunning_main() != null){
                                Log.e(NAME, "refresh the Main Activity...");
                                /** call {@link MainActivity#refresh()} */
                                MyApp.getRunning_main().refresh();
                            }
                        });
                    }
                }
            }
        }).start();
    }

    /**
     * @ui
     * @clear
     */
    private void setHintForEditText(String hint, int size, EditText et){
        SpannableString h = new SpannableString(hint);
        AbsoluteSizeSpan s = new AbsoluteSizeSpan(size,true);//true means "sp"
        h.setSpan(s, 0, h.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        et.setHint(new SpannedString(h));
    }
}


