'use strict';

import { NativeModules } from 'react-native';
import { mapParameters } from './utils';

const Braintree = NativeModules.Braintree;

module.exports = {
  setup(token) {
    return new Promise(function(resolve, reject) {
      Braintree.setup(token, test => resolve(test), err => reject(err));
    });
  },


  showPaymentViewController(config = {}) {
    var options = {
      callToActionText: config.callToActionText,
      title: config.title,
      description: config.description,
      amount: config.amount,
    };
    return new Promise(function(resolve, reject) {
      Braintree.paymentRequest(
          options,
          nonce => resolve(nonce),
          error => reject(error)
      );
    });
  },
  showVenmoViewController(options = {}) {
    return new Promise(function(resolve, reject) {
      Braintree.venmoRequest(options, venmoData => resolve(venmoData), error => reject(error));
    });
  },
  showPayPalViewController(options = {}) {
    return new Promise(function(resolve, reject) {
      Braintree.paypalRequest(options, paypalData => resolve(paypalData), error => reject(error));
    });
  },
  showSamsungPayViewController(options = {}) {
    return new Promise(function(resolve, reject) {
      Braintree.samsungPayRequest(options, samsungPayData => resolve(samsungPayData), error => reject(error));
    });
  },
  isSamsungPayEnabled(options = {}) {
    return new Promise(function(resolve, reject) {
      Braintree.isSamsungPayEnabled(options, enabled => resolve(enabled), error => reject(error));
    });
  },
  getCardNonce(parameters = {}) {
    return new Promise(function(resolve, reject) {
      Braintree.getCardNonce(mapParameters(parameters), nonce => resolve(nonce), err => reject(err)
      );
    });

  },
};
