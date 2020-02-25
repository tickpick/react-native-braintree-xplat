package com.pw.droplet.braintree;

import java.util.Map;
import java.util.HashMap;

import com.braintreepayments.api.DataCollector;
import com.braintreepayments.api.SamsungPay;
import com.braintreepayments.api.SamsungPayAvailability;
import com.braintreepayments.api.Venmo;
import com.braintreepayments.api.GooglePayment;

import com.braintreepayments.api.GooglePaymentActivity;
import com.braintreepayments.api.interfaces.BraintreeCancelListener;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.interfaces.SamsungPayCustomTransactionUpdateListener;
import com.braintreepayments.api.models.GooglePaymentCardNonce;
import com.braintreepayments.api.models.GooglePaymentRequest;
import com.braintreepayments.api.models.PayPalAccountNonce;
import com.braintreepayments.api.models.PayPalRequest;
import com.braintreepayments.api.models.PostalAddress;
import com.braintreepayments.api.models.SamsungPayNonce;
import com.braintreepayments.api.models.VenmoAccountNonce;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.google.gson.Gson;

import android.content.Intent;
import android.content.Context;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.braintreepayments.api.PaymentRequest;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.BraintreePaymentActivity;
import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.exceptions.BraintreeError;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.PayPal;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.ReadableMap;
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.*;


import static com.samsung.android.sdk.samsungpay.v2.SpaySdk.*;

public class Braintree extends ReactContextBaseJavaModule implements ActivityEventListener {
    private static final int PAYMENT_REQUEST = 1706816330;
    private String token;

    private Callback successCallback;
    private Callback errorCallback;

    private Context mActivityContext;

    private BraintreeFragment mBraintreeFragment;
    private PaymentManager mPaymentManager;
    private CustomSheetPaymentInfo mPaymentInfo;
    private String mNonce;

    public Braintree(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "Braintree";
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @ReactMethod
    public void setup(final String token, final Callback successCallback, final Callback errorCallback) {
        try {
            this.mBraintreeFragment = BraintreeFragment.newInstance(getCurrentActivity(), token);
            this.mBraintreeFragment.addListener(new BraintreeCancelListener() {
                @Override
                public void onCancel(int requestCode) {
                    nonceErrorCallback("USER_CANCELLATION");
                }
            });
            this.mBraintreeFragment.addListener(new PaymentMethodNonceCreatedListener() {
                @Override
                public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
                    String nonce = paymentMethodNonce.getNonce();

                    if (paymentMethodNonce instanceof VenmoAccountNonce) {
                        VenmoAccountNonce venmoAccountNonce = (VenmoAccountNonce) paymentMethodNonce;
                        venmoCallback(venmoAccountNonce);
                    }
                    else if (paymentMethodNonce instanceof SamsungPayNonce) {
                        SamsungPayNonce samsungPayNonce = (SamsungPayNonce) paymentMethodNonce;
                        mNonce = samsungPayNonce.getNonce();
                        samsungPayCallback();
                        // PaymentMethodNonce returned from Samsung Pay.
                        // Send this to your server.
                    }
                    else if (paymentMethodNonce instanceof GooglePaymentCardNonce) {
                        GooglePaymentCardNonce googplePlayNonce = (GooglePaymentCardNonce) paymentMethodNonce;
                        googlePlayCallback(googplePlayNonce);
                    }
                    else if (paymentMethodNonce instanceof PayPalAccountNonce) {
                        PayPalAccountNonce paypalAccountNonce = (PayPalAccountNonce) paymentMethodNonce;
                        paypalCallback(paypalAccountNonce);
                    }
                    else{
                        nonceCallback(nonce);
                    }

                }
            });
            this.mBraintreeFragment.addListener(new BraintreeErrorListener() {
                @Override
                public void onError(Exception error) {
                    if (error instanceof ErrorWithResponse) {
                        ErrorWithResponse errorWithResponse = (ErrorWithResponse) error;
                        BraintreeError cardErrors = errorWithResponse.errorFor("creditCard");
                        if (cardErrors != null) {
                            Gson gson = new Gson();
                            final Map<String, String> errors = new HashMap<>();
                            BraintreeError numberError = cardErrors.errorFor("number");
                            BraintreeError cvvError = cardErrors.errorFor("cvv");
                            BraintreeError expirationDateError = cardErrors.errorFor("expirationDate");
                            BraintreeError postalCode = cardErrors.errorFor("postalCode");

                            if (numberError != null) {
                                errors.put("card_number", numberError.getMessage());
                            }

                            if (cvvError != null) {
                                errors.put("cvv", cvvError.getMessage());
                            }

                            if (expirationDateError != null) {
                                errors.put("expiration_date", expirationDateError.getMessage());
                            }

                            // TODO add more fields
                            if (postalCode != null) {
                                errors.put("postal_code", postalCode.getMessage());
                            }

                            nonceErrorCallback(gson.toJson(errors));
                        } else {
                            nonceErrorCallback(errorWithResponse.getErrorResponse());
                        }
                    }
                }
            });
            this.setToken(token);
            successCallback.invoke(this.getToken());
        } catch (InvalidArgumentException e) {
            errorCallback.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void getCardNonce(final ReadableMap parameters, final Callback successCallback, final Callback errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;

        CardBuilder cardBuilder = new CardBuilder()
                .validate(false);

        if (parameters.hasKey("number"))
            cardBuilder.cardNumber(parameters.getString("number"));

        if (parameters.hasKey("cvv"))
            cardBuilder.cvv(parameters.getString("cvv"));

        // In order to keep compatibility with iOS implementation, do not accept expirationMonth and exporationYear,
        // accept rather expirationDate (which is combination of expirationMonth/expirationYear)
        if (parameters.hasKey("expirationDate"))
            cardBuilder.expirationDate(parameters.getString("expirationDate"));

        if (parameters.hasKey("cardholderName"))
            cardBuilder.cardholderName(parameters.getString("cardholderName"));

        if (parameters.hasKey("firstName"))
            cardBuilder.firstName(parameters.getString("firstName"));

        if (parameters.hasKey("lastName"))
            cardBuilder.lastName(parameters.getString("lastName"));

        if (parameters.hasKey("company"))
            cardBuilder.company(parameters.getString("company"));

    /*
    if (parameters.hasKey("countryName"))
      cardBuilder.countryName(parameters.getString("countryName"));

    if (parameters.hasKey("countryCodeAlpha2"))
      cardBuilder.countryCodeAlpha2(parameters.getString("countryCodeAlpha2"));

    if (parameters.hasKey("countryCodeAlpha3"))
      cardBuilder.countryCodeAlpha3(parameters.getString("countryCodeAlpha3"));

    if (parameters.hasKey("countryCodeNumeric"))
      cardBuilder.countryCodeNumeric(parameters.getString("countryCodeNumeric"));
*/
        if (parameters.hasKey("locality"))
            cardBuilder.locality(parameters.getString("locality"));

        if (parameters.hasKey("postalCode"))
            cardBuilder.postalCode(parameters.getString("postalCode"));

        if (parameters.hasKey("region"))
            cardBuilder.region(parameters.getString("region"));

        if (parameters.hasKey("streetAddress"))
            cardBuilder.streetAddress(parameters.getString("streetAddress"));

        if (parameters.hasKey("extendedAddress"))
            cardBuilder.extendedAddress(parameters.getString("extendedAddress"));

        Card.tokenize(this.mBraintreeFragment, cardBuilder);
    }

    public void nonceCallback(String nonce) {
        this.successCallback.invoke(nonce);
    }


    public void venmoCallback(VenmoAccountNonce vman) {
        final WritableMap res = Arguments.createMap();
        res.putString("nonce", vman.getNonce());
        res.putString("username", vman.getUsername());
        res.putString("typeLabel", vman.getTypeLabel());
        res.putString("description", vman.getDescription());

        DataCollector.collectDeviceData(this.mBraintreeFragment, new BraintreeResponseListener<String>() {
            @Override
            public void onResponse(String s) {
                res.putString("deviceData", s);
                Braintree.this.successCallback.invoke(res);
            }
        });
    }

    public void googlePlayCallback(GooglePaymentCardNonce gpn) {
        final WritableMap res = Arguments.createMap();
        res.putString("nonce", gpn.getNonce());
    /*
    UserAddress pa = gpn.getBillingAddress();
    final WritableMap billing = Arguments.createMap();
    billing.putString("name", pa.getRecipientName());
    billing.putString("phone", pa.getPhoneNumber());
    billing.putString("street_address", pa.getStreetAddress());
    billing.putString("street_address2", pa.getExtendedAddress());
    billing.putString("city", pa.getLocality());
    billing.putString("state", pa.getRegion());
    billing.putString("country", pa.getCountryCodeAlpha2());
    billing.putString("zip", pa.getPostalCode());
    res.putMap("billing_address", billing);
    */
        Braintree.this.successCallback.invoke(res);
    }

    public void paypalCallback(PayPalAccountNonce ppan) {
        final WritableMap res = Arguments.createMap();
        res.putString("nonce", ppan.getNonce());
        PostalAddress pa = ppan.getBillingAddress();
        final WritableMap billing = Arguments.createMap();
        billing.putString("name", pa.getRecipientName());
        billing.putString("phone", pa.getPhoneNumber());
        billing.putString("street_address", pa.getStreetAddress());
        billing.putString("street_address2", pa.getExtendedAddress());
        billing.putString("city", pa.getLocality());
        billing.putString("state", pa.getRegion());
        billing.putString("country", pa.getCountryCodeAlpha2());
        billing.putString("zip", pa.getPostalCode());

        res.putMap("billing_address", billing);



        Braintree.this.successCallback.invoke(res);


    }

    public void samsungPayCallback(){

        if(mNonce != null && mPaymentInfo != null){
            final WritableMap res = Arguments.createMap();
            res.putString("nonce", mNonce);

            CustomSheet customSheet = mPaymentInfo.getCustomSheet();
            AddressControl billingAddressControl = (AddressControl) customSheet.getSheetControl("billingAddressId");
            CustomSheetPaymentInfo.Address billingAddress = billingAddressControl.getAddress();

            final WritableMap billing = Arguments.createMap();
            billing.putString("name", billingAddress.getAddressee());
            billing.putString("phone", billingAddress.getPhoneNumber());
            billing.putString("street_address", billingAddress.getAddressLine1());
            billing.putString("street_address2", billingAddress.getAddressLine2());
            billing.putString("city", billingAddress.getCity());
            billing.putString("state", billingAddress.getState());
            billing.putString("country", billingAddress.getCountryCode());
            billing.putString("zip", billingAddress.getPostalCode());

            res.putMap("billing_address", billing);

            CustomSheetPaymentInfo.Address shippingAddress = mPaymentInfo.getPaymentShippingAddress();

            if(shippingAddress != null && shippingAddress.getPostalCode() != null && !shippingAddress.getPostalCode().isEmpty()){
                final WritableMap shipping = Arguments.createMap();
                shipping.putString("name", billingAddress.getAddressee());
                shipping.putString("phone", billingAddress.getPhoneNumber());
                shipping.putString("street_address", shippingAddress.getAddressLine1());
                shipping.putString("street_address2", shippingAddress.getAddressLine2());
                shipping.putString("city", shippingAddress.getCity());
                shipping.putString("state", shippingAddress.getState());
                shipping.putString("country", shippingAddress.getCountryCode());
                shipping.putString("zip", shippingAddress.getPostalCode());

                res.putMap("shipping_address", shipping);
            }
            Braintree.this.successCallback.invoke(res);
        }
    }

    public void nonceErrorCallback(String error) {
        this.errorCallback.invoke(error);
    }

    @ReactMethod
    public void paymentRequest(final ReadableMap options, final Callback successCallback, final Callback errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;
        PaymentRequest paymentRequest = null;

        String callToActionText = null;
        String title = null;
        String description = null;
        String amount = null;

        if (options.hasKey("callToActionText")) {
            callToActionText = options.getString("callToActionText");
        }

        if (options.hasKey("title")) {
            title = options.getString("title");
        }

        if (options.hasKey("description")) {
            description = options.getString("description");
        }

        if (options.hasKey("amount")) {
            amount = options.getString("amount");
        }

        paymentRequest = new PaymentRequest()
                .submitButtonText(callToActionText)
                .primaryDescription(title)
                .secondaryDescription(description)
                .amount(amount)
                .clientToken(this.getToken());

        (getCurrentActivity()).startActivityForResult(
                paymentRequest.getIntent(getCurrentActivity()),
                PAYMENT_REQUEST
        );
    }

    @ReactMethod
    public void paypalRequest(final ReadableMap options, final Callback successCallback, final Callback errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;

        PayPalRequest request = new PayPalRequest(options.getString("amount"))
                .currencyCode("USD")
                .intent(PayPalRequest.INTENT_AUTHORIZE);

        PayPal.requestOneTimePayment(this.mBraintreeFragment, request);
    }

    @ReactMethod
    public void venmoRequest(final ReadableMap options, final Callback successCallback, final Callback errorCallback) {
        if(Venmo.isVenmoInstalled(getReactApplicationContext())){
            this.successCallback = successCallback;
            this.errorCallback = errorCallback;
            Venmo.authorizeAccount(this.mBraintreeFragment, false);
        }
        else{
            errorCallback.invoke("NOT_INSTALLED");
        }


    }


    @ReactMethod
    public void isSamsungPayEnabled(final ReadableMap options, final Callback successCallback, final Callback errorCallback) {
        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                switch (availability.getStatus()) {
                    case SPAY_READY:
                        successCallback.invoke(true);
                        break;
                    case SPAY_NOT_READY:
                        Integer reason = availability.getReason();

                        errorCallback.invoke("Not ready ["+reason+"]");
                    /*
                    if (reason == ERROR_SPAY_APP_NEED_TO_UPDATE) {
                        // Samsung Pay's app needs an update.
                        // Let Samsung Pay navigate to the update page.
                        SamsungPay.goToUpdatePage(mBraintreeFragment);
                    } else if (reason == ERROR_SPAY_SETUP_NOT_COMPLETED) {
                        // Samsung Pay's activation setup is not complete.
                        // Let Samsung Pay's app continue to activate.
                        SamsungPay.activateSamsungPay(mBraintreeFragment);
                    } else if (reason == SamsungPay.SPAY_NO_SUPPORTED_CARDS_IN_WALLET) {
                        // Samsung Pay has no supported cards currently.
                    }
                    */
                        break;

                    case SPAY_NOT_SUPPORTED:
                        errorCallback.invoke("Not supported");
                        // Samsung Pay is not currently supported on this device.
                        break;
                }
            }
        });
    }

    @ReactMethod
    public void samsungPayRequest(final ReadableMap options, final Callback successCallback, final Callback errorCallback) {
        mPaymentInfo = null;
        mNonce = null;

        this.successCallback = successCallback;
        this.errorCallback = errorCallback;

        SamsungPay.createPaymentManager(mBraintreeFragment, new BraintreeResponseListener<PaymentManager>() {
            @Override
            public void onResponse(PaymentManager paymentManager) {
                mPaymentManager = paymentManager;

                SamsungPay.createPaymentInfo(mBraintreeFragment, new BraintreeResponseListener<CustomSheetPaymentInfo.Builder>() {
                    @Override
                    public void onResponse(CustomSheetPaymentInfo.Builder builder) {
                        if(options.getBoolean("isFedEx")){
                            builder.setAddressInPaymentSheet(CustomSheetPaymentInfo.AddressInPaymentSheet.NEED_BILLING_AND_SHIPPING);
                        }
                        else{
                            builder.setAddressInPaymentSheet(CustomSheetPaymentInfo.AddressInPaymentSheet.NEED_BILLING_SPAY);
                        }

                        builder.setOrderNumber(options.getString("orderNumber"));

                        CustomSheetPaymentInfo paymentInfo = builder.setCustomSheet(getCustomSheet(Double.parseDouble(options.getString("amount")), options.getBoolean("isFedEx"))).setCardHolderNameEnabled(true).setRecurringEnabled(false).build();

                        SamsungPay.requestPayment(mBraintreeFragment, mPaymentManager, paymentInfo, new SamsungPayCustomTransactionUpdateListener() {
                            @Override
                            public void onSuccess(CustomSheetPaymentInfo response, Bundle extraPaymentData) {
                                mPaymentInfo = response;
                                samsungPayCallback();
                            }
                            @Override
                            public void onCardInfoUpdated(@NonNull CardInfo cardInfo, @NonNull CustomSheet customSheet) {
                                if(cardInfo != null && cardInfo.getCardMetaData() != null) {
                                    for (String key : cardInfo.getCardMetaData().keySet()) {
                                        Log.e("cardInfo", key + " is a key in the bundle");
                                    }
                                }


                                mPaymentManager.updateSheet(customSheet);
                            }
                        });
                    }
                });
            }
        });



    }


    private CustomSheet getCustomSheet(double amount, boolean isFedEx) {
        CustomSheet sheet = new CustomSheet();


        AddressControl billingAddressControl = new AddressControl("billingAddressId", SheetItemType.BILLING_ADDRESS);
        billingAddressControl.setAddressTitle("Billing Address");
        billingAddressControl.setSheetUpdatedListener(new SheetUpdatedListener() {
            @Override
            public void onResult(String controlId, final CustomSheet customSheet) {
                mPaymentManager.updateSheet(customSheet);
            }
        });


        sheet.addControl(billingAddressControl);



        if(isFedEx) {
            AddressControl shippingAddressControl = new AddressControl("shippingAddressId", SheetItemType.SHIPPING_ADDRESS);
            shippingAddressControl.setAddressTitle("Shipping Address");

            shippingAddressControl.setSheetUpdatedListener(new SheetUpdatedListener() {
                @Override
                public void onResult(String controlId, final CustomSheet customSheet) {
                    //AmountBoxControl amountBoxControl = (AmountBoxControl) customSheet.getSheetControl("amountID");
                    mPaymentManager.updateSheet(customSheet);
                }
            });

            sheet.addControl(shippingAddressControl);
        }
        AmountBoxControl amountBoxControl = new AmountBoxControl("amountID", "USD");
        amountBoxControl.setAmountTotal(amount, AmountConstants.FORMAT_TOTAL_PRICE_ONLY);
        sheet.addControl(amountBoxControl);

        return sheet;
    }

    @ReactMethod
    public void googlePayRequest(final ReadableMap options, final Callback successCallback, final Callback errorCallback) {

        GooglePayment.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<Boolean>() {
            @Override
            public void onResponse(Boolean isReadyToPay) {
                if (isReadyToPay) {
                    // Show Google Pay button
                    Braintree.this.successCallback = successCallback;
                    Braintree.this.errorCallback = errorCallback;

                    GooglePaymentRequest googlePaymentRequest = new GooglePaymentRequest();
          /*TransactionInfo ti = new TransactionInfo();
          googlePaymentRequest.tr()
          .transactionInfo(TransactionInfo.newBuilder()
                          .setTotalPrice("1.00")
                          .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                          .setCurrencyCode("USD")
                          .build())
                  // We recommend collecting billing address information, at minimum
                  // billing postal code, and passing that billing postal code with all
                  // Google Pay card transactions as a best practice.
                  .billingAddressRequired(true)
                  // Optional in sandbox; if set in sandbox, this value must be a valid production Google Merchant ID
                  .googleMerchantId("merchant-id-from-google");
                  */
                    GooglePayment.requestPayment(mBraintreeFragment, googlePaymentRequest);
                }
            }
        });
    }
    @Override
    public void onActivityResult(Activity activity, final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == PAYMENT_REQUEST) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    PaymentMethodNonce paymentMethodNonce = data.getParcelableExtra(
                            BraintreePaymentActivity.EXTRA_PAYMENT_METHOD_NONCE
                    );
                    this.successCallback.invoke(paymentMethodNonce.getNonce());
                    break;
                case BraintreePaymentActivity.BRAINTREE_RESULT_DEVELOPER_ERROR:
                case BraintreePaymentActivity.BRAINTREE_RESULT_SERVER_ERROR:
                case BraintreePaymentActivity.BRAINTREE_RESULT_SERVER_UNAVAILABLE:
                    this.errorCallback.invoke(
                            data.getSerializableExtra(BraintreePaymentActivity.EXTRA_ERROR_MESSAGE)
                    );
                    break;
                case Activity.RESULT_CANCELED:
                    this.errorCallback.invoke("USER_CANCELLATION");
                    break;
                default:
                    break;
            }
        }
    }

    public void onNewIntent(Intent intent){}
}
