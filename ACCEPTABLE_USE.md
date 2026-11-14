# Safety & Ethical Guidelines

**Project:** Pocket Agent  
**License:** GNU Affero General Public License v3.0 (AGPL-3.0)

Pocket Agent is an autonomous assistive on-device AI agent designed to empower individuals by operating their own Android devices through voice and accessibility automation. Because the software interacts with system accessibility and notification APIs, these guidelines outline our community expectations, intended use cases, and design safeguards.

---

## 1. Intended Purpose

Pocket Agent is built for:
* **Personal Automation**: Hands-free device assistance, voice task completion, and routine automation on hardware owned and controlled by the user.
* **Accessibility**: Empowering users with visual, motor, or situational impairments to navigate complex mobile interfaces.
* **Open-Source Research**: Transparent evaluation of on-device LLM reasoning, mobile UI grounding, and privacy-preserving agent architectures.

---

## 2. Responsible Development & Prohibited Uses

Pocket Agent is distributed under the GNU AGPL-3.0 in the spirit of open-source collaboration. Contributors and downstream developers are expected to uphold ethical standards:

### A. Respect User Consent & Privacy (No Spyware)
* Do not install or operate Pocket Agent without the device owner's explicit knowledge and ongoing consent.
* Do not weaponize accessibility services for unauthorized surveillance, clandestine background tracking, or stalkerware.

### B. Protect Financial Integrity & Credentials
* Do not circumvent or disable the deterministic safety mechanisms (`SafetyGate`, `BlockedApps`, Keystore encryption) to execute unauthorized transactions, fund transfers, or automated purchases.
* Do not repurpose the screen parser or accessibility hooks to harvest passwords, PINs, 2FA tokens, or credit card numbers.

### C. Prevent Malware & Harmful Automation
* Do not package Pocket Agent into deceptive apps, adware, or trojans.
* Do not utilize the automation engine for bulk spamming, phishing, or automated harassment.

---

## 3. Core Safety Safeguards

Pocket Agent implements deterministic on-device safety gates in code:
1. **App Blocklist**: Banking, financial, crypto, and credential management packages are strictly refused by default.
2. **Human-in-the-Loop Confirmation**: Irreversible actions (*Pay*, *Send*, *Delete*, *Order*) require explicit human approval.
3. **Local Encryption**: All API credentials are encrypted with Android Keystore AES-256 GCM.
4. **Data Isolation**: Untrusted UI content is sanitized to prevent prompt injection.

Downstream maintainers and contributors are strongly encouraged to retain these protective boundaries in all user-facing distributions.

---

## 4. Disclaimer of Liability

Pocket Agent is provided "as is", without warranty of any kind, express or implied, as detailed in the [AGPL-3.0 License](LICENSE). The authors and contributors:
1. Disclaim all liability for any damages, unauthorized actions, or legal violations resulting from third-party modifications, unauthorized repackaging, or misuse of this software.
2. Strongly condemn any malicious repurposing of mobile accessibility technologies.

Users and developers assume full responsibility for compliance with all applicable local and international laws.

---

## 5. Responsible Disclosure

To report security vulnerabilities or safety concerns, please follow our disclosure process in [SECURITY.md](SECURITY.md).
