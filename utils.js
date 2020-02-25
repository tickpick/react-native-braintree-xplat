// @flow

import { Platform } from 'react-native';

import type {
  CardParameters,
  AndroidCardParameters,
  IOSCardParameters,
} from './types';

export function mapParameters(
    parameters: CardParameters
): AndroidCardParameters | IOSCardParameters {
  if (Platform.OS === 'android') {
    return parameters;
  }

  // iOS field mapping
  // https://github.com/braintree/braintree_ios/blob/master/BraintreeCard/BTCard.m#L14
  return {
    number: parameters.number,
    cvv: parameters.cvv,
    expiration_date: parameters.expirationDate,
    cardholder_name: parameters.cardholderName,
    billing_address: {
      postal_code: parameters.postalCode,
      street_address: parameters.streetAddress,
      extended_address: parameters.extendedAddress,
      locality: parameters.locality,
      region: parameters.region,
      country_name: parameters.countryName,
      country_code_alpha2: parameters.countryCodeAlpha2,
      country_code_alpha3: parameters.countryCodeAlpha3,
      country_code_numeric: parameters.countryCodeNumeric,
      first_name: parameters.firstName,
      last_name: parameters.lastName,
      company: parameters.company,
    },
  };
}
