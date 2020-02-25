// @flow

export type CardParameters = {
  number: string,
  cvv: string,
  expirationDate: string,
  cardholderName: string,
  firstName: string,
  lastName: string,
  company: string,
  countryName: string,
  countryCodeAlpha2: string,
  countryCodeAlpha3: string,
  countryCodeNumeric: string,
  locality: string,
  postalCode: string,
  region: string,
  streetAddress: string,
  extendedAddress: string,
};

export type IOSCardParameters = {
  number: string,
  cvv: string,
  expiration_date: string,
  cardholder_name: string,
  billing_address: {
    postal_code: string,
    street_address: string,
    extended_address: string,
    locality: string,
    region: string,
    country_name: string,
    country_code_alpha2: string,
    country_code_alpha3: string,
    country_code_numeric: string,
    first_name: string,
    last_name: string,
    company: string,
  },
};

export type AndroidCardParameters = CardParameters;
