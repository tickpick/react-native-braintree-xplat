// @flow

'use strict';

import { NativeModules, processColor } from 'react-native';
import { mapParameters } from './utils';

import type { CardParameters } from './types';

const RCTBraintree = NativeModules.Braintree;

var Braintree = {
  setupWithURLScheme(token, urlscheme) {
    return new Promise(function(resolve, reject) {
      RCTBraintree.setupWithURLScheme(token, urlscheme, function(success) {
        success == true ? resolve(true) : reject('Invalid Token');
      });
    });
  },

  setup(token) {
    return new Promise(function(resolve, reject) {
      RCTBraintree.setup(token, function(success) {
        success == true ? resolve(true) : reject('Invalid Token');
      });
    });
  },

  showPaymentViewController(config = {}) {
    var options = {
      tintColor: processColor(config.tintColor),
      bgColor: processColor(config.bgColor),
      barBgColor: processColor(config.barBgColor),
      barTintColor: processColor(config.barTintColor),
      callToActionText: config.callToActionText,
      title: config.title,
      description: config.description,
      amount: config.amount,
    };
    return new Promise(function(resolve, reject) {
      RCTBraintree.showPaymentViewController(options, function(err, nonce) {
        nonce != null ? resolve(nonce) : reject(err);
      });
    });
  },


  showVenmoViewController(options = {}) {
    return new Promise(function(resolve, reject) {
      RCTBraintree.showVenmoViewController(options, function(err, venmoData) {
        venmoData != null ? resolve(venmoData) : reject(err);
      });
    });
  },
  showPayPalViewController(options = {}) {
    return new Promise(function(resolve, reject) {
      RCTBraintree.showPayPalViewController(options, function(err, paypalData) {
        paypalData != null ? resolve(paypalData) : reject(err);
      });
    });
  },

  async getCardNonce(parameters: CardParameters = {}) {
    try {
      const nonce = await RCTBraintree.getCardNonce(mapParameters(parameters));

      return nonce;
    } catch (error) {
      throw error;
    }
  },

  showApplePayViewController(options = {}) {
    return new Promise(function(resolve, reject) {
      RCTBraintree.showApplePayViewController(options, function(err, cardData) {
        cardData != null ? resolve(cardData) : reject(err);
      });
    });
  },
  completeApplePay(status) {
    return new Promise(function(resolve, reject) {
      RCTBraintree.completeApplePay(status, function(success) {
        success == true ? resolve(true) : reject('Failed');
      });
    });
  },
  getDeviceData(options = {}) {
    return new Promise(function(resolve, reject) {
      RCTBraintree.getDeviceData(options, function(err, deviceData) {
        deviceData != null ? resolve(deviceData) : reject(err);
      });
    });
  },
};

module.exports = Braintree;
