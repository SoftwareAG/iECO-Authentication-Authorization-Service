## SSI OIDC login


The SSI OIDC login flow. Participants are:

- User agent: browser. 
- AAS: [Authentication & Authorization Service](https://www.gxfs.eu/authentication-authorisation/), GAIA-X LOT1 implementation.
- TSA: [Trust Service API](https://www.gxfs.eu/trust-services-api/), GAIA-X LOT4 implementation.
- Portal: [Portal](https://www.gxfs.eu/portal/) web application, GAIA-X LOT13 implementation.
- IAM Platform: Identity and Access Management solution like keycloak, Gluu, WSO2, etc. 

![SSI OIDC login](./images/ssi_ciba_login.png "SSI OIDC login")

Data Flow Diagram for SSI OIDC login is:

![OIDC Login DFD](./images/oidc_login_dfd.png "OIDC Login DFD")