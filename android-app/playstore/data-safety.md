# Play Console — Data safety form answers

Use these answers in **Play Console → App content → Data safety**.

## Data collection and sharing
- **Does your app collect or share any of the required user data types?** → **No**
  - SyncMesh transmits clipboard text and files **only** directly between the user's own
    paired devices on their local network. This data is never sent to the developer or any
    third party, and is not collected off-device. Under Play's definitions, data that stays
    on the user's own devices and is not sent to you or third parties is **not** "collected"
    or "shared."

## Security practices
- **Is data encrypted in transit?** → Data is transferred directly on the user's local
  network (peer-to-peer). It is **not** sent to servers. (SyncMesh does not add its own
  transport encryption; it operates only on networks the user trusts. Do not claim TLS.)
- **Can users request data deletion?** → Users can delete all data on-device by clearing
  history or uninstalling. No server-side data exists.

## Other declarations
- **Contains ads:** No
- **In-app purchases:** No
- **Account creation required:** No
- **Target audience / content rating:** Everyone (Tools app; no objectionable content).

## Content rating questionnaire (IARC) — expected answers
- Violence / sexual / profanity / controlled substances / gambling: **None**
- User-generated content shared publicly: **No** (transfers are private, device-to-device)
- Shares user location: **No**
- Expected result: **Everyone**

> Honesty note: SyncMesh currently sends clipboard/file data in plaintext over the local
> network and stores clipboard history locally in plaintext. This is consistent with the
> answers above (no off-device collection), but if you later add cloud features or transport
> encryption, update this form accordingly.
