//
//  RCTBraintree.m
//  RCTBraintree
//
//  Created by Rickard Ekman on 18/06/16.
//  Copyright © 2016 Rickard Ekman. All rights reserved.
//

#import "RCTBraintree.h"

@implementation RCTBraintree {
    bool runCallback;
}

static NSString *URLScheme;

+ (instancetype)sharedInstance {
    static RCTBraintree *_sharedInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        _sharedInstance = [[RCTBraintree alloc] init];
    });
    return _sharedInstance;
}

- (instancetype)init
{
    if ((self = [super init])) {
        self.dataCollector = [[BTDataCollector alloc]
                              initWithEnvironment:BTDataCollectorEnvironmentProduction];
    }
    return self;
}

RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(setupWithURLScheme:(NSString *)clientToken urlscheme:(NSString*)urlscheme callback:(RCTResponseSenderBlock)callback)
{
    URLScheme = urlscheme;
    [BTAppSwitch setReturnURLScheme:urlscheme];
    self.braintreeClient = [[BTAPIClient alloc] initWithAuthorization:clientToken];
    if (self.braintreeClient == nil) {
        callback(@[@false]);
    }
    else {
        callback(@[@true]);
    }
}

RCT_EXPORT_METHOD(setup:(NSString *)clientToken callback:(RCTResponseSenderBlock)callback)
{
    self.braintreeClient = [[BTAPIClient alloc] initWithAuthorization:clientToken];
    if (self.braintreeClient == nil) {
        callback(@[@false]);
    }
    else {
        callback(@[@true]);
    }
}

RCT_EXPORT_METHOD(showPaymentViewController:(NSDictionary *)options callback:(RCTResponseSenderBlock)callback)
{
    dispatch_async(dispatch_get_main_queue(), ^{
        self.threeDSecureOptions = options[@"threeDSecure"];
        if (self.threeDSecureOptions) {
            self.threeDSecure = [[BTThreeDSecureDriver alloc] initWithAPIClient:self.braintreeClient delegate:self];
        }

        BTDropInViewController *dropInViewController = [[BTDropInViewController alloc] initWithAPIClient:self.braintreeClient];
        dropInViewController.delegate = self;

        NSLog(@"%@", options);

        UIColor *tintColor = options[@"tintColor"];
        UIColor *bgColor = options[@"bgColor"];
        UIColor *barBgColor = options[@"barBgColor"];
        UIColor *barTintColor = options[@"barTintColor"];

        NSString *title = options[@"title"];
        NSString *description = options[@"description"];
        NSString *amount = options[@"amount"];

        if (tintColor) dropInViewController.view.tintColor = [RCTConvert UIColor:tintColor];
        if (bgColor) dropInViewController.view.backgroundColor = [RCTConvert UIColor:bgColor];

        dropInViewController.navigationItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemCancel target:self action:@selector(userDidCancelPayment)];

        self.callback = callback;

        UINavigationController *navigationController = [[UINavigationController alloc] initWithRootViewController:dropInViewController];

        if (barBgColor) navigationController.navigationBar.barTintColor = [RCTConvert UIColor:barBgColor];
        if (barTintColor) navigationController.navigationBar.tintColor = [RCTConvert UIColor:barTintColor];

        if (options[@"callToActionText"]) {
            BTPaymentRequest *paymentRequest = [[BTPaymentRequest alloc] init];
            paymentRequest.callToActionText = options[@"callToActionText"];

            dropInViewController.paymentRequest = paymentRequest;
        }

        if (title) [dropInViewController.paymentRequest setSummaryTitle:title];
        if (description) [dropInViewController.paymentRequest setSummaryDescription:description];
        if (amount) [dropInViewController.paymentRequest setDisplayAmount:amount];

        [self.reactRoot presentViewController:navigationController animated:YES completion:nil];
    });
}

RCT_EXPORT_METHOD(showPayPalViewController:(NSDictionary *)options callback:(RCTResponseSenderBlock)callback)
{
    dispatch_async(dispatch_get_main_queue(), ^{

        BTPayPalDriver *payPalDriver = [[BTPayPalDriver alloc] initWithAPIClient:self.braintreeClient];
        payPalDriver.viewControllerPresentingDelegate = self;

        NSLog(@"%@", options);

        BTPayPalRequest *request= [[BTPayPalRequest alloc] initWithAmount:options[@"amount"]];
        request.currencyCode = @"USD";

        [payPalDriver requestOneTimePayment:request completion:^(BTPayPalAccountNonce *tokenizedPayPalAccount, NSError *error) {
            NSMutableArray *args = @[[NSNull null]];
            if ( error == nil && tokenizedPayPalAccount != nil ) {

                NSDictionary *billingAddress = @{
                                                 @"name": [NSString stringWithFormat:@"%@ %@", tokenizedPayPalAccount.firstName, tokenizedPayPalAccount.lastName],
                                                 @"phone": tokenizedPayPalAccount.phone,
                                                 @"street_address": tokenizedPayPalAccount.billingAddress.streetAddress,
                                                 @"street_address2": tokenizedPayPalAccount.billingAddress.extendedAddress,
                                                 @"city": tokenizedPayPalAccount.billingAddress.locality,
                                                 @"state": tokenizedPayPalAccount.billingAddress.region,
                                                 @"country": tokenizedPayPalAccount.billingAddress.countryCodeAlpha2,
                                                 @"zip": tokenizedPayPalAccount.billingAddress.postalCode
                                                 };
                args = @[[NSNull null],  @{
                             @"billing_address": billingAddress,
                             @"nonce": tokenizedPayPalAccount.nonce,
                             }];

                if (tokenizedPayPalAccount.phone != nil) {
                }
                if (tokenizedPayPalAccount.billingAddress!= nil) {
                }

            } else if ( error != nil ) {
                args = @[error.description, [NSNull null]];
            }

            callback(args);
        }];
    });
}

RCT_REMAP_METHOD(getCardNonce,
                 parameters:(NSDictionary *)parameters
                 resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject)
{
    BTCardClient *cardClient = [[BTCardClient alloc] initWithAPIClient: self.braintreeClient];
    BTCard *card = [[BTCard alloc] initWithParameters:parameters];
    //card.shouldValidate = YES;

    NSLog(@"Card: %@", parameters);
    [cardClient tokenizeCard:card
                  completion:^(BTCardNonce *tokenizedCard, NSError *error) {

                      if ( error == nil ) {
                          resolve(tokenizedCard.nonce);
                      } else {
                          NSLog(@"Error: %@", error);
                          reject(@"Error getting nonce", @"Cannot process this credit card type.", error);
                      }
                  }];
}


RCT_EXPORT_METHOD(getDeviceData:(NSDictionary *)options callback:(RCTResponseSenderBlock)callback)
{
    dispatch_async(dispatch_get_main_queue(), ^{

        NSLog(@"%@", options);

        NSError *error = nil;
        NSString *deviceData = nil;
        NSString *environment = options[@"environment"];
        NSString *dataSelector = options[@"dataCollector"];

        //Initialize the data collector and specify environment
        if([environment isEqualToString: @"development"]){
            self.dataCollector = [[BTDataCollector alloc]
                                  initWithEnvironment:BTDataCollectorEnvironmentDevelopment];
        } else if([environment isEqualToString: @"qa"]){
            self.dataCollector = [[BTDataCollector alloc]
                                  initWithEnvironment:BTDataCollectorEnvironmentQA];
        } else if([environment isEqualToString: @"sandbox"]){
            self.dataCollector = [[BTDataCollector alloc]
                                  initWithEnvironment:BTDataCollectorEnvironmentSandbox];
        }

        //Data collection methods
        if ([dataSelector isEqualToString: @"card"]){
            deviceData = [self.dataCollector collectCardFraudData];
        } else if ([dataSelector isEqualToString: @"both"]){
            deviceData = [self.dataCollector collectFraudData];
        } else if ([dataSelector isEqualToString: @"paypal"]){
            deviceData = [PPDataCollector collectPayPalDeviceData];
        } else {
            NSMutableDictionary* details = [NSMutableDictionary dictionary];
            [details setValue:@"Invalid data collector" forKey:NSLocalizedDescriptionKey];
            error = [NSError errorWithDomain:@"RCTBraintree" code:255 userInfo:details];
            NSLog (@"Invalid data collector. Use one of: card, paypal or both");
        }

        NSArray *args = @[];
        if ( error == nil ) {
            args = @[[NSNull null], deviceData];
        } else {
            args = @[error.description, [NSNull null]];
        }

        callback(args);
    });
}

RCT_EXPORT_METHOD(showVenmoViewController:(NSDictionary *)options callback:(RCTResponseSenderBlock)callback)
{
    dispatch_async(dispatch_get_main_queue(), ^{
        BTVenmoDriver *venmoDriver = [[BTVenmoDriver alloc] initWithAPIClient:self.braintreeClient];
            [venmoDriver authorizeAccountAndVault:NO completion:^(BTVenmoAccountNonce *tokenizedVenmoAccount, NSError *error) {
            if ( error == nil && tokenizedVenmoAccount != nil ) {
                NSString *deviceData = [PPDataCollector collectPayPalDeviceData];
                callback(@[[NSNull null], @{
                               @"nonce": tokenizedVenmoAccount.nonce,
                               @"username": tokenizedVenmoAccount.username,
                               @"deviceData": deviceData
                               }]);
            } else if ( error != nil ) {
                 callback(@[error.description, [NSNull null]]);
            }

        }];

    });
}

RCT_EXPORT_METHOD(showApplePayViewController:(NSDictionary *)options callback:(RCTResponseSenderBlock)callback)
{
    dispatch_async(dispatch_get_main_queue(), ^{
        self.callback = callback;
        PKPaymentRequest *paymentRequest = [[PKPaymentRequest alloc] init];
        NSArray *items = options[@"paymentSummaryItems"];
        NSLog(@"Options items: %@", items);
        NSMutableArray *paymentSummaryItems = [NSMutableArray new];
        for(NSDictionary *item in items) {
            NSString *label = item[@"label"];
            NSString *amount = [item[@"amount"] stringValue];
            [paymentSummaryItems addObject:[PKPaymentSummaryItem summaryItemWithLabel:label amount:[NSDecimalNumber decimalNumberWithString:amount]]];
        }

        paymentRequest.requiredBillingAddressFields = PKAddressFieldNone;
        paymentRequest.shippingMethods = nil;
        paymentRequest.requiredShippingAddressFields = PKAddressFieldNone;
        paymentRequest.paymentSummaryItems = paymentSummaryItems;

        paymentRequest.merchantIdentifier = options[@"merchantIdentifier"];;
        paymentRequest.supportedNetworks = @[PKPaymentNetworkVisa, PKPaymentNetworkMasterCard, PKPaymentNetworkAmex, PKPaymentNetworkDiscover];
        paymentRequest.merchantCapabilities = PKMerchantCapability3DS;
        paymentRequest.currencyCode = @"USD";
        paymentRequest.countryCode = @"US";
        if ([paymentRequest respondsToSelector:@selector(setShippingType:)]) {
            paymentRequest.shippingType = PKShippingTypeDelivery;
        }

        PKPaymentAuthorizationViewController *viewController = [[PKPaymentAuthorizationViewController alloc] initWithPaymentRequest:paymentRequest];
        viewController.delegate = self;

        [self.reactRoot presentViewController:viewController animated:YES completion:nil];
    });
}

- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url sourceApplication:(NSString *)sourceApplication annotation:(id)annotation {

    if ([url.scheme localizedCaseInsensitiveCompare:URLScheme] == NSOrderedSame) {
        return [BTAppSwitch handleOpenURL:url sourceApplication:sourceApplication];
    }
    return NO;
}

#pragma mark - BTViewControllerPresentingDelegate

- (void)paymentDriver:(id)paymentDriver requestsPresentationOfViewController:(UIViewController *)viewController {
    [self.reactRoot presentViewController:viewController animated:YES completion:nil];
}

- (void)paymentDriver:(id)paymentDriver requestsDismissalOfViewController:(UIViewController *)viewController {
    if (!viewController.isBeingDismissed) {
        [viewController.presentingViewController dismissViewControllerAnimated:YES completion:nil];
    }
}

#pragma mark - BTDropInViewControllerDelegate

- (void)userDidCancelPayment {
    [self.reactRoot dismissViewControllerAnimated:YES completion:nil];
    runCallback = FALSE;
    self.callback(@[@"USER_CANCELLATION", [NSNull null]]);
}

- (void)dropInViewControllerWillComplete:(BTDropInViewController *)viewController {
    runCallback = TRUE;
}

- (void)dropInViewController:(BTDropInViewController *)viewController didSucceedWithTokenization:(BTPaymentMethodNonce *)paymentMethodNonce {
    // when the user pays for the first time with paypal, dropInViewControllerWillComplete is never called, yet the callback should be invoked.  the second condition checks for that
    if (runCallback || ([paymentMethodNonce.type isEqualToString:@"PayPal"] && [viewController.paymentMethodNonces count] == 1)) {
        if (self.threeDSecure) {
            [self.reactRoot dismissViewControllerAnimated:YES completion:nil];
            [self.threeDSecure verifyCardWithNonce:paymentMethodNonce.nonce
                                            amount:self.threeDSecureOptions[@"amount"]
                                        completion:^(BTThreeDSecureCardNonce *card, NSError *error) {
                                            if (runCallback) {
                                                runCallback = FALSE;
                                                if (error) {
                                                    self.callback(@[error.localizedDescription, [NSNull null]]);
                                                } else if (card) {
                                                    if (!card.liabilityShiftPossible) {
                                                        self.callback(@[@"3DSECURE_NOT_ABLE_TO_SHIFT_LIABILITY", [NSNull null]]);
                                                    } else if (!card.liabilityShifted) {
                                                        self.callback(@[@"3DSECURE_LIABILITY_NOT_SHIFTED", [NSNull null]]);
                                                    } else {
                                                        self.callback(@[[NSNull null], card.nonce]);
                                                    }
                                                } else {
                                                    self.callback(@[@"USER_CANCELLATION", [NSNull null]]);
                                                }
                                            }
                                            [self.reactRoot dismissViewControllerAnimated:YES completion:nil];
                                        }];
        } else {
            runCallback = FALSE;
            self.callback(@[[NSNull null], paymentMethodNonce.nonce]);
        }
    }

    if (!self.threeDSecure) {
        [self.reactRoot dismissViewControllerAnimated:YES completion:nil];
    }
}

- (void)dropInViewControllerDidCancel:(__unused BTDropInViewController *)viewController {
    self.callback(@[@"Drop-In ViewController Closed", [NSNull null]]);
    [viewController dismissViewControllerAnimated:YES completion:nil];
}

- (UIViewController*)reactRoot {
    UIViewController *root  = [UIApplication sharedApplication].keyWindow.rootViewController;
    UIViewController *maybeModal = root.presentedViewController;

    UIViewController *modalRoot = root;

    if (maybeModal != nil) {
        modalRoot = maybeModal;
    }

    return modalRoot;
}

#pragma mark PKPaymentAuthorizationViewControllerDelegate

- (void)paymentAuthorizationViewController:(__unused PKPaymentAuthorizationViewController *)controller
                       didAuthorizePayment:(PKPayment *)payment
                                completion:(void (^)(PKPaymentAuthorizationStatus status))completion
{
    NSLog(@"paymentAuthorizationViewController:didAuthorizePayment");
    BTApplePayClient *applePayClient = [[BTApplePayClient alloc] initWithAPIClient:self.braintreeClient];
    [applePayClient tokenizeApplePayPayment:payment completion:^(BTApplePayCardNonce * _Nullable tokenizedApplePayPayment, NSError * _Nullable error) {
        if (error) {
            completion(PKPaymentAuthorizationStatusFailure);
            self.callback(@[@"Error processing card", [NSNull null]]);
        } else {
            self.callback(@[[NSNull null], @{
                                @"nonce": tokenizedApplePayPayment.nonce,
                                @"type": tokenizedApplePayPayment.type,
                                @"localizedDescription": tokenizedApplePayPayment.localizedDescription
                                }]);
            completion(PKPaymentAuthorizationStatusSuccess);
        }
    }];
}

- (void)paymentAuthorizationViewControllerDidFinish:(PKPaymentAuthorizationViewController *)controller {
    // Just close the view controller. We either succeeded or the user hit cancel.
    [self.reactRoot dismissViewControllerAnimated:YES completion:nil];
}

- (void)paymentAuthorizationViewControllerWillAuthorizePayment:(PKPaymentAuthorizationViewController *)controller {
    // Move along. Nothing to see here.
}

@end
