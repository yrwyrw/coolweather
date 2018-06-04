package com.coolweather.android;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.coolweather.android.db.City;
import com.coolweather.android.db.County;
import com.coolweather.android.db.Province;
import com.coolweather.android.util.BackHandlerHelper;
import com.coolweather.android.util.HttpUtil;
import com.coolweather.android.util.Utility;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private LocationClient mLocationClient;

    private String provinceName;
    private String cityName;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        mLocationClient = new LocationClient(getApplicationContext());
        mLocationClient.registerLocationListener(new MyLocationListener());
        List<String> permissionList = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!permissionList.isEmpty()) {
            String [] permissions = permissionList.toArray(new String[permissionList.size()]);
            ActivityCompat.requestPermissions(MainActivity.this, permissions, 1);
        } else {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (prefs.getString("weather", null) != null) {
                Intent intent = new Intent(this, WeatherActivity.class);
                startActivity(intent);
                finish();
            }
            Log.d("MyActivity", "ByeBye");
        }

    }

    private void requestLocation() {
        initLocation();
        mLocationClient.start();
    }

    private void initLocation(){
        LocationClientOption option = new LocationClientOption();
        option.setIsNeedAddress(true);
        mLocationClient.setLocOption(option);
    }

    private class MyLocationListener implements BDLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            provinceName = location.getProvince();
            cityName = location.getCity();
            provinceName = provinceName.substring(0, provinceName.length() - 1);
            cityName = cityName.substring(0, cityName.length() - 1);

            provinceGet();
        }
    }

    private void provinceGet() {
        String address = "http://guolin.tech/api/china";
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                if(Utility.handleProvinceResponse(responseText)) {
                    List<Province> provinceList = DataSupport.findAll(Province.class);
                    if(provinceList.size() > 0) {
                        for(Province p : provinceList) {
                            if(p.getProvinceName().equals(provinceName)){
                                cityGet(p);
                                Log.d("MyActivity", p.getProvinceName() + p.getProvinceCode());
                                break;
                            }
                        }
                    }
                }

            }
        });
    }

    private void cityGet(final Province province) {
        String address = "http://guolin.tech/api/china/" + province.getProvinceCode();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                if(Utility.handleCityResponse(responseText, province.getId())) {
                    List<City> cityList = DataSupport.where("provinceid = ?", String.valueOf(province.getId()))
                            .find(City.class);
                    if(cityList.size() > 0) {
                        for(City city : cityList) {
                            if(city.getCityName().equals(cityName)){
                                countyGet(province, city);
                                Log.d("MyActivity", city.getCityName() + city.getCityCode() + " " + city.getProvinceId());
                                break;
                            }
                        }
                    }
                }

            }
        });
    }

    private void countyGet(final Province province, final City city) {
        String address = "http://guolin.tech/api/china/" + province.getProvinceCode() + "/" + city.getCityCode();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                if(Utility.handleCountyResponse(responseText, city.getId())) {
                    List<County> countyList = DataSupport.where("cityid = ?", String.valueOf(city.getId()))
                            .find(County.class);
                    if(countyList.size() > 0) {
                        for(County county : countyList) {
                            if(county.getCountyName().equals(cityName)){
                                Log.d("MyActivity", county.getCountyName() + county.getWeatherId());
                                Intent intent = new Intent(context, WeatherActivity.class);
                                intent.putExtra("weather_id", county.getWeatherId());
                                startActivity(intent);
                                ((Activity) context).finish();
                                break;
                            }
                        }
                    }
                }

            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLocationClient.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0) {
                    for (int result : grantResults) {
                        if (result != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "必须同意所有权限才能使用本程序", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                    }
                    requestLocation();
                } else {
                    Toast.makeText(this, "发生未知错误", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
        }
    }

    @Override
    public void onBackPressed() {
        if (!BackHandlerHelper.handleBackPress(this)) {
            super.onBackPressed();
        }
    }
}
