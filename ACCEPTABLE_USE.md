# Acceptable Use Policy (AUP)

**Project:** Pocket Agent  
**Last Updated:** November 2026

Pocket Agent is an autonomous assistive on-device AI agent designed to empower individuals by operating their own Android devices through voice and accessibility automation. Because the software interacts with system accessibility and notification APIs, this Acceptable Use Policy outlines the strict rules and ethical boundaries governing its use, distribution, and modification.

---

## 1. Intended Purpose

Pocket Agent is intended exclusively for:
* Personal device assistance, accessibility enhancement, and task automation on hardware owned or explicitly authorized by the user.
* Open-source research, developer experimentation, and evaluation of on-device LLM reasoning and mobile accessibility frameworks.

---

## 2. Prohibited Activities

You may **not** use, modify, redistribute, or incorporate Pocket Agent (in whole or in part) for any of the following purposes:

### A. Surveillance & Spyware
* Installing or running Pocket Agent on any device without the device owner's explicit, informed consent.
* Repackaging the app to secretly monitor screens, intercept private messages, or log user behavior (stalkerware / spyware).

### B. Financial Fraud & Credential Theft
* Disabling, weakening, or circumventing the deterministic safety mechanisms (including `SafetyGate`, `BlockedApps`, or confirmation prompts) to execute unauthorized financial transactions, money transfers, or e-commerce purchases.
* Harvesting passwords, PINs, cryptographic keys, two-factor authentication tokens, or payment card details.

### C. Malware & Unauthorized Device Control
* Distributing modified versions disguised as benign software or trojans.
* Weaponizing accessibility gestures to inject adware, spam, bulk messaging, or denial-of-service actions.
* Exfiltrating private screen or notification contents to unauthorized third-party servers.

### D. Harassment, Impersonation & Abuse
* Generating deceptive, abusive, harassing, or defamatory communications through automated messaging or social platforms.
* Impersonating another person or organization without authorization.

---

## 3. Preservation of Safety Safeguards

When creating derivative works or distributing builds:
* The core safety boundaries (blocking sensitive financial/banking applications, masking authentication codes, and requiring explicit human-in-the-loop confirmation for irreversible actions) should not be removed or bypassed in builds distributed to general users.
* If testing modifications in development environments, developers must maintain equivalent safeguards to protect end-user security.

---

## 4. Disclaimer of Liability & Indemnification

The authors and contributors of Pocket Agent:
1. Provide the software "as is", without warranty of any kind.
2. Are not responsible for any damage, loss of data, unauthorized transactions, or legal violations arising from modified versions, unauthorized distributions, or malicious use of this software.
3. Strongly condemn any malicious repurposing of mobile accessibility technologies.

Any person or entity utilizing this software agrees to assume full legal responsibility for their actions and compliance with applicable local and international laws.

---

## 5. Reporting Violations & Vulnerabilities

To report misuse, malicious repackaging, or security vulnerabilities, please refer to [docs/SECURITY_MODEL.md](docs/SECURITY_MODEL.md) or open a confidential security advisory on GitHub.
