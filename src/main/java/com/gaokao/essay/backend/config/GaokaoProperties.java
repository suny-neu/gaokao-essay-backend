package com.gaokao.essay.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gaokao")
public class GaokaoProperties {

  private String authTokenSecret = "change-me-local-secret";
  private long authTokenExpireSeconds = 604800L;
  private boolean localAuthFallbackEnabled = false;
  private boolean requestOpenIdFallbackEnabled = false;
  private final Storage storage = new Storage();
  private final Runtime runtime = new Runtime();
  private final Knowledge knowledge = new Knowledge();
  private final Wechat wechat = new Wechat();
  private final Security security = new Security();
  private final Ai ai = new Ai();
  private final Ocr ocr = new Ocr();
  private final Membership membership = new Membership();
  private final Payment payment = new Payment();

  public String getAuthTokenSecret() {
    return authTokenSecret;
  }

  public void setAuthTokenSecret(String authTokenSecret) {
    this.authTokenSecret = authTokenSecret;
  }

  public long getAuthTokenExpireSeconds() {
    return authTokenExpireSeconds;
  }

  public void setAuthTokenExpireSeconds(long authTokenExpireSeconds) {
    this.authTokenExpireSeconds = authTokenExpireSeconds;
  }

  public boolean isLocalAuthFallbackEnabled() {
    return localAuthFallbackEnabled;
  }

  public void setLocalAuthFallbackEnabled(boolean localAuthFallbackEnabled) {
    this.localAuthFallbackEnabled = localAuthFallbackEnabled;
  }

  public boolean isRequestOpenIdFallbackEnabled() {
    return requestOpenIdFallbackEnabled;
  }

  public void setRequestOpenIdFallbackEnabled(boolean requestOpenIdFallbackEnabled) {
    this.requestOpenIdFallbackEnabled = requestOpenIdFallbackEnabled;
  }

  public int getTrialTotalLimit() {
    return membership.getTrialTotalLimit();
  }

  public void setTrialTotalLimit(int trialTotalLimit) {
    this.membership.setTrialTotalLimit(trialTotalLimit);
  }

  public int getTrialDailyLimit() {
    return membership.getTrialDailyLimit();
  }

  public void setTrialDailyLimit(int trialDailyLimit) {
    this.membership.setTrialDailyLimit(trialDailyLimit);
  }

  public boolean isBillingDebugEnabled() {
    return membership.isAllowDebugSubscriptionActivate();
  }

  public void setBillingDebugEnabled(boolean billingDebugEnabled) {
    membership.setAllowDebugSubscriptionActivate(billingDebugEnabled);
  }

  public Storage getStorage() {
    return storage;
  }

  public Runtime getRuntime() {
    return runtime;
  }

  public Knowledge getKnowledge() {
    return knowledge;
  }

  public Wechat getWechat() {
    return wechat;
  }

  public Security getSecurity() {
    return security;
  }

  public Ai getAi() {
    return ai;
  }

  public Ocr getOcr() {
    return ocr;
  }

  public Membership getMembership() {
    return membership;
  }

  public Payment getPayment() {
    return payment;
  }

  public static class Storage {
    private String stateFile = "./data/runtime-state.json";
    private final Database database = new Database();

    public String getStateFile() {
      return stateFile;
    }

    public void setStateFile(String stateFile) {
      this.stateFile = stateFile;
    }

    public Database getDatabase() {
      return database;
    }
  }

  public static class Database {
    private boolean enabled;
    private String url = "";
    private String username = "";
    private String password = "";
    private String driverClassName = "org.postgresql.Driver";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getDriverClassName() {
      return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
      this.driverClassName = driverClassName;
    }

    public String resolveKind() {
      String signal = (driverClassName + " " + url).toLowerCase();
      if (signal.contains("postgres")) {
        return "postgres";
      }
      if (signal.contains("mysql")) {
        return "mysql";
      }
      return "database";
    }
  }

  public static class Runtime {
    private boolean strictStartupChecks;

    public boolean isStrictStartupChecks() {
      return strictStartupChecks;
    }

    public void setStrictStartupChecks(boolean strictStartupChecks) {
      this.strictStartupChecks = strictStartupChecks;
    }
  }

  public static class Knowledge {
    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  public static class Wechat {
    private String appId = "";
    private String appSecret = "";
    private boolean strictCode2Session;
    private String code2SessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
    private String stableTokenUrl = "https://api.weixin.qq.com/cgi-bin/stable_token";
    private String msgSecCheckUrl = "https://api.weixin.qq.com/wxa/msg_sec_check";

    public String getAppId() {
      return appId;
    }

    public void setAppId(String appId) {
      this.appId = appId;
    }

    public String getAppSecret() {
      return appSecret;
    }

    public void setAppSecret(String appSecret) {
      this.appSecret = appSecret;
    }

    public boolean isStrictCode2Session() {
      return strictCode2Session;
    }

    public void setStrictCode2Session(boolean strictCode2Session) {
      this.strictCode2Session = strictCode2Session;
    }

    public String getCode2SessionUrl() {
      return code2SessionUrl;
    }

    public void setCode2SessionUrl(String code2SessionUrl) {
      this.code2SessionUrl = code2SessionUrl;
    }

    public String getStableTokenUrl() {
      return stableTokenUrl;
    }

    public void setStableTokenUrl(String stableTokenUrl) {
      this.stableTokenUrl = stableTokenUrl;
    }

    public String getMsgSecCheckUrl() {
      return msgSecCheckUrl;
    }

    public void setMsgSecCheckUrl(String msgSecCheckUrl) {
      this.msgSecCheckUrl = msgSecCheckUrl;
    }
  }

  public static class Security {
    private boolean msgSecEnabled;
    private boolean healthDetailsEnabled;
    private boolean healthIssueDetailsEnabled;
    private boolean rateLimitEnabled = true;
    private int authPerMinute = 12;
    private int essaySubmitPerMinute = 5;
    private int historyReadPerMinute = 60;
    private int ocrPerMinute = 5;
    private long maxUploadBytes = 5L * 1024L * 1024L;
    private boolean challengeEnabled = true;
    private boolean redisRequired;
    private long challengeTtlSeconds = 60L;
    private int challengePerMinute = 10;

    public boolean isMsgSecEnabled() {
      return msgSecEnabled;
    }

    public void setMsgSecEnabled(boolean msgSecEnabled) {
      this.msgSecEnabled = msgSecEnabled;
    }

    public boolean isHealthDetailsEnabled() {
      return healthDetailsEnabled;
    }

    public void setHealthDetailsEnabled(boolean healthDetailsEnabled) {
      this.healthDetailsEnabled = healthDetailsEnabled;
    }

    public boolean isHealthIssueDetailsEnabled() {
      return healthIssueDetailsEnabled;
    }

    public void setHealthIssueDetailsEnabled(boolean healthIssueDetailsEnabled) {
      this.healthIssueDetailsEnabled = healthIssueDetailsEnabled;
    }

    public boolean isRateLimitEnabled() {
      return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
      this.rateLimitEnabled = rateLimitEnabled;
    }

    public int getAuthPerMinute() {
      return authPerMinute;
    }

    public void setAuthPerMinute(int authPerMinute) {
      this.authPerMinute = authPerMinute;
    }

    public int getEssaySubmitPerMinute() {
      return essaySubmitPerMinute;
    }

    public void setEssaySubmitPerMinute(int essaySubmitPerMinute) {
      this.essaySubmitPerMinute = essaySubmitPerMinute;
    }

    public int getHistoryReadPerMinute() {
      return historyReadPerMinute;
    }

    public void setHistoryReadPerMinute(int historyReadPerMinute) {
      this.historyReadPerMinute = historyReadPerMinute;
    }

    public int getOcrPerMinute() {
      return ocrPerMinute;
    }

    public void setOcrPerMinute(int ocrPerMinute) {
      this.ocrPerMinute = ocrPerMinute;
    }

    public long getMaxUploadBytes() {
      return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
      this.maxUploadBytes = maxUploadBytes;
    }

    public boolean isChallengeEnabled() {
      return challengeEnabled;
    }

    public boolean isRedisRequired() {
      return redisRequired;
    }

    public void setRedisRequired(boolean redisRequired) {
      this.redisRequired = redisRequired;
    }

    public void setChallengeEnabled(boolean challengeEnabled) {
      this.challengeEnabled = challengeEnabled;
    }

    public long getChallengeTtlSeconds() {
      return challengeTtlSeconds;
    }

    public void setChallengeTtlSeconds(long challengeTtlSeconds) {
      this.challengeTtlSeconds = challengeTtlSeconds;
    }

    public int getChallengePerMinute() {
      return challengePerMinute;
    }

    public void setChallengePerMinute(int challengePerMinute) {
      this.challengePerMinute = challengePerMinute;
    }
  }

  public static class Ai {
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private String providerName = "openai-compatible";
    private double temperature = 0.7D;
    private int timeoutSeconds = 90;

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public String getProviderName() {
      return providerName;
    }

    public void setProviderName(String providerName) {
      this.providerName = providerName;
    }

    public double getTemperature() {
      return temperature;
    }

    public void setTemperature(double temperature) {
      this.temperature = temperature;
    }

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }
  }

  public static class Ocr {
    private boolean enabled;
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private String providerName = "openai-compatible";
    private int timeoutSeconds = 90;
    private String tencentSecretId = "";
    private String tencentSecretKey = "";
    private String tencentRegion = "";
    private String tencentAction = "GeneralAccurateOCR";
    private String tencentVersion = "2018-11-19";
    private String tencentLanguageType = "zh";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public String getProviderName() {
      return providerName;
    }

    public void setProviderName(String providerName) {
      this.providerName = providerName;
    }

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }

    public String getTencentSecretId() {
      return tencentSecretId;
    }

    public void setTencentSecretId(String tencentSecretId) {
      this.tencentSecretId = tencentSecretId;
    }

    public String getTencentSecretKey() {
      return tencentSecretKey;
    }

    public void setTencentSecretKey(String tencentSecretKey) {
      this.tencentSecretKey = tencentSecretKey;
    }

    public String getTencentRegion() {
      return tencentRegion;
    }

    public void setTencentRegion(String tencentRegion) {
      this.tencentRegion = tencentRegion;
    }

    public String getTencentAction() {
      return tencentAction;
    }

    public void setTencentAction(String tencentAction) {
      this.tencentAction = tencentAction;
    }

    public String getTencentVersion() {
      return tencentVersion;
    }

    public void setTencentVersion(String tencentVersion) {
      this.tencentVersion = tencentVersion;
    }

    public String getTencentLanguageType() {
      return tencentLanguageType;
    }

    public void setTencentLanguageType(String tencentLanguageType) {
      this.tencentLanguageType = tencentLanguageType;
    }
  }

  public static class Membership {
    private int trialTotalLimit;
    private int trialDailyLimit = 5;
    private int deviceDailyLimit = 4;
    private int ipDailyLimit = 20;
    private String quotaZoneId = "Asia/Shanghai";
    private boolean allowDebugSubscriptionActivate = true;
    private final AdReward adReward = new AdReward();
    private final Plan monthly = new Plan("monthly", "包月会员", "30 天不限次作文生成与严格批改", 30, 3900, false, false);
    private final Plan annual = new Plan("annual", "包年会员", "365 天不限次，更适合长期备考", 365, 29900, true, false);
    private final Plan founderLifetime = new Plan("founder_lifetime", "创始终身会员", "终身不限量作文生成与严格批改", 0, 59900, false, true);

    public int getTrialTotalLimit() {
      return trialTotalLimit;
    }

    public void setTrialTotalLimit(int trialTotalLimit) {
      this.trialTotalLimit = trialTotalLimit;
    }

    public int getTrialDailyLimit() {
      return trialDailyLimit;
    }

    public void setTrialDailyLimit(int trialDailyLimit) {
      this.trialDailyLimit = trialDailyLimit;
    }

    public int getDeviceDailyLimit() {
      return deviceDailyLimit;
    }

    public void setDeviceDailyLimit(int deviceDailyLimit) {
      this.deviceDailyLimit = deviceDailyLimit;
    }

    public int getIpDailyLimit() {
      return ipDailyLimit;
    }

    public void setIpDailyLimit(int ipDailyLimit) {
      this.ipDailyLimit = ipDailyLimit;
    }

    public String getQuotaZoneId() {
      return quotaZoneId;
    }

    public void setQuotaZoneId(String quotaZoneId) {
      this.quotaZoneId = quotaZoneId;
    }

    public boolean isAllowDebugSubscriptionActivate() {
      return allowDebugSubscriptionActivate;
    }

    public void setAllowDebugSubscriptionActivate(boolean allowDebugSubscriptionActivate) {
      this.allowDebugSubscriptionActivate = allowDebugSubscriptionActivate;
    }

    public Plan getMonthly() {
      return monthly;
    }

    public Plan getAnnual() {
      return annual;
    }

    public Plan getFounderLifetime() {
      return founderLifetime;
    }

    public AdReward getAdReward() {
      return adReward;
    }
  }

  public static class AdReward {
    private boolean enabled = true;
    private int creditPerView = 1;
    private int dailyLimit = 5;
    private int cooldownSeconds = 30;
    private int maxCredits = 50;
    private int claimNotBeforeSeconds = 3;
    private int claimTtlSeconds = 600;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getCreditPerView() {
      return Math.max(creditPerView, 1);
    }

    public void setCreditPerView(int creditPerView) {
      this.creditPerView = creditPerView;
    }

    public int getDailyLimit() {
      return Math.max(dailyLimit, 0);
    }

    public void setDailyLimit(int dailyLimit) {
      this.dailyLimit = dailyLimit;
    }

    public int getGrantPerView() {
      return getCreditPerView();
    }

    public void setGrantPerView(int grantPerView) {
      setCreditPerView(grantPerView);
    }

    public int getDailyMax() {
      return getDailyLimit();
    }

    public void setDailyMax(int dailyMax) {
      setDailyLimit(dailyMax);
    }

    public int getCooldownSeconds() {
      return Math.max(cooldownSeconds, 0);
    }

    public void setCooldownSeconds(int cooldownSeconds) {
      this.cooldownSeconds = cooldownSeconds;
    }

    public int getMaxCredits() {
      return Math.max(maxCredits, 1);
    }

    public void setMaxCredits(int maxCredits) {
      this.maxCredits = maxCredits;
    }

    public int getClaimNotBeforeSeconds() {
      return Math.max(claimNotBeforeSeconds, 0);
    }

    public void setClaimNotBeforeSeconds(int claimNotBeforeSeconds) {
      this.claimNotBeforeSeconds = claimNotBeforeSeconds;
    }

    public int getClaimTtlSeconds() {
      return Math.max(claimTtlSeconds, 30);
    }

    public void setClaimTtlSeconds(int claimTtlSeconds) {
      this.claimTtlSeconds = claimTtlSeconds;
    }
  }

  public static class Payment {
    private boolean enabled;
    private String notifyUrl = "";
    private String currency = "CNY";
    private String orderDescriptionPrefix = "高考英语作文会员";
    private final WechatPay wechat = new WechatPay();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getNotifyUrl() {
      return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
      this.notifyUrl = notifyUrl;
    }

    public String getCurrency() {
      return currency;
    }

    public void setCurrency(String currency) {
      this.currency = currency;
    }

    public String getOrderDescriptionPrefix() {
      return orderDescriptionPrefix;
    }

    public void setOrderDescriptionPrefix(String orderDescriptionPrefix) {
      this.orderDescriptionPrefix = orderDescriptionPrefix;
    }

    public WechatPay getWechat() {
      return wechat;
    }
  }

  public static class WechatPay {
    private String merchantId = "";
    private String merchantSerialNumber = "";
    private String privateKeyPem = "";
    private String privateKeyFile = "";
    private String platformPublicKeyPem = "";
    private String platformPublicKeyFile = "";
    private String platformSerialNumber = "";
    private String apiV3Key = "";
    private String jsapiUrl = "https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi";
    private String orderQueryBaseUrl = "https://api.mch.weixin.qq.com";

    public String getMerchantId() {
      return merchantId;
    }

    public void setMerchantId(String merchantId) {
      this.merchantId = merchantId;
    }

    public String getMerchantSerialNumber() {
      return merchantSerialNumber;
    }

    public void setMerchantSerialNumber(String merchantSerialNumber) {
      this.merchantSerialNumber = merchantSerialNumber;
    }

    public String getPrivateKeyPem() {
      return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
      this.privateKeyPem = privateKeyPem;
    }

    public String getPrivateKeyFile() {
      return privateKeyFile;
    }

    public void setPrivateKeyFile(String privateKeyFile) {
      this.privateKeyFile = privateKeyFile;
    }

    public String getPlatformPublicKeyPem() {
      return platformPublicKeyPem;
    }

    public void setPlatformPublicKeyPem(String platformPublicKeyPem) {
      this.platformPublicKeyPem = platformPublicKeyPem;
    }

    public String getPlatformPublicKeyFile() {
      return platformPublicKeyFile;
    }

    public void setPlatformPublicKeyFile(String platformPublicKeyFile) {
      this.platformPublicKeyFile = platformPublicKeyFile;
    }

    public String getPlatformSerialNumber() {
      return platformSerialNumber;
    }

    public void setPlatformSerialNumber(String platformSerialNumber) {
      this.platformSerialNumber = platformSerialNumber;
    }

    public String getApiV3Key() {
      return apiV3Key;
    }

    public void setApiV3Key(String apiV3Key) {
      this.apiV3Key = apiV3Key;
    }

    public String getJsapiUrl() {
      return jsapiUrl;
    }

    public void setJsapiUrl(String jsapiUrl) {
      this.jsapiUrl = jsapiUrl;
    }

    public String getOrderQueryBaseUrl() {
      return orderQueryBaseUrl;
    }

    public void setOrderQueryBaseUrl(String orderQueryBaseUrl) {
      this.orderQueryBaseUrl = orderQueryBaseUrl;
    }
  }

  public static class Plan {
    private String code = "";
    private String name = "";
    private String description = "";
    private int durationDays;
    private int priceFen;
    private boolean recommended;
    private boolean lifetime;

    public Plan() {
    }

    public Plan(String code, String name, String description, int durationDays, int priceFen, boolean recommended) {
      this(code, name, description, durationDays, priceFen, recommended, false);
    }

    public Plan(String code, String name, String description, int durationDays, int priceFen, boolean recommended, boolean lifetime) {
      this.code = code;
      this.name = name;
      this.description = description;
      this.durationDays = durationDays;
      this.priceFen = priceFen;
      this.recommended = recommended;
      this.lifetime = lifetime;
    }

    public String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public int getDurationDays() {
      return durationDays;
    }

    public void setDurationDays(int durationDays) {
      this.durationDays = durationDays;
    }

    public int getPriceFen() {
      return priceFen;
    }

    public void setPriceFen(int priceFen) {
      this.priceFen = priceFen;
    }

    public boolean isRecommended() {
      return recommended;
    }

    public void setRecommended(boolean recommended) {
      this.recommended = recommended;
    }

    public boolean isLifetime() {
      return lifetime;
    }

    public void setLifetime(boolean lifetime) {
      this.lifetime = lifetime;
    }
  }
}
