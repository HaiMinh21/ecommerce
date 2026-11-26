package com.adidas.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("shopeepay")
public class ShopeePayProperties {
    private boolean enabled;
    private String baseUrl = "https://api.uat.wallet.airpay.vn";
    private String clientId;
    private String secretKey;
    private String merchantExtId;
    private String storeExtId;
    private String responseSignatureHeader = "X-Airpay-Resp-H";
    private String callbackSignatureHeader = "X-Airpay-Req-H";
}
