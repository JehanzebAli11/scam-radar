"""Prompt templates for the teacher model (generation and labelling)."""

from __future__ import annotations

from scamradar.schema import FLAG_ORDER, SCAM_TYPES

LANGUAGES = ("roman_urdu", "urdu", "english", "mixed")

LANGUAGE_NAMES = {
    "roman_urdu": "Roman Urdu (Urdu written in Latin letters, as typed on phones)",
    "urdu": "Urdu in Urdu (Nastaliq/Arabic) script",
    "english": "Pakistani English",
    "mixed": "code-mixed Roman Urdu and English",
}

GENERATE_SYSTEM = (
    "You write realistic but entirely fictional SMS and WhatsApp messages received by "
    "people in Pakistan, to train a scam-detection model that protects them. Use only "
    "fictional names, and placeholder-style numbers (e.g. 0300-0000000, 00000-0000000-0). "
    "Never include real people's details. Output one message per line, no numbering, "
    "no commentary."
)

#: Per-category guidance. Keys are scam types plus safe/promo categories and the
#: hard-negative categories (genuine messages that look scammy).
CATEGORY_GUIDANCE: dict[str, str] = {
    "fake_govt_aid": (
        "Scams pretending to be BISP, Ehsaas, PM relief or another government programme, "
        "asking for a fee, CNIC details or a call to an ordinary mobile number."
    ),
    "fake_prize": (
        "Scams announcing a lottery, lucky draw or prize (e.g. from a TV show, telco or "
        "brand) that requires a fee, tax or code to claim."
    ),
    "code_theft": (
        "Scams that trick the recipient into sharing a one-time code (WhatsApp, JazzCash, "
        "Easypaisa, bank OTP) e.g. 'code galti se aap ko chala gaya'."
    ),
    "bank_or_wallet_impersonation": (
        "Scams impersonating a bank or mobile wallet helpline claiming the account is "
        "blocked or needs verification, asking for PIN, CNIC or card details."
    ),
    "family_emergency": (
        "Scams pretending to be a relative or friend in trouble (accident, police, new "
        "number) urgently asking for money via a wallet transfer."
    ),
    "fake_job_or_investment": (
        "Scams offering easy online jobs, part-time task work, crypto or investment "
        "schemes with guaranteed daily profit, requiring a registration fee."
    ),
    "phishing_link": (
        "Scams whose main hook is a link to a fake login, parcel-tracking, tax refund or "
        "verification page."
    ),
    "other": "Other scams not covered above (fake rishta, fake loan app, sim upgrade, etc.).",
    "safe_personal": "Ordinary personal messages between family and friends.",
    "safe_transactional": (
        "Genuine transactional alerts: debit/credit alerts, bill reminders, appointment "
        "confirmations, school notices."
    ),
    "promo": (
        "Legitimate marketing: telco bundle offers, shop sales, restaurant deals, with "
        "official short codes and no request for codes or personal details."
    ),
    # Hard negatives: genuine messages that share surface features with scams.
    "hard_negative_bank_otp": (
        "Genuine bank or wallet OTP messages that contain a code and explicitly say "
        "'do not share this code with anyone', 'bank kabhi code nahi maangta'."
    ),
    "hard_negative_bisp_8171": (
        "Genuine BISP messages sent from the official 8171 short code telling recipients "
        "to visit a BISP centre with their CNIC; no fee and no link."
    ),
    "hard_negative_courier_code": (
        "Genuine courier/delivery messages (TCS, Leopards, M&P style but fictional) with a "
        "tracking number or delivery code to show the rider."
    ),
    "hard_negative_telco_promo": (
        "Genuine telco promos (fictional operator names) with dial codes like *123# and "
        "bundle prices; no link to a login page and no request for codes."
    ),
}

HARD_NEGATIVE_CATEGORIES = tuple(k for k in CATEGORY_GUIDANCE if k.startswith("hard_negative_"))

#: Tags attached to generated records per category (used by the eval harness).
CATEGORY_TAGS: dict[str, tuple[str, ...]] = {
    "hard_negative_bank_otp": ("otp", "hard_negative"),
    "hard_negative_bisp_8171": ("hard_negative",),
    "hard_negative_courier_code": ("hard_negative",),
    "hard_negative_telco_promo": ("hard_negative",),
}


def generation_prompt(category: str, seed_text: str, language: str, n: int) -> list[dict[str, str]]:
    if category not in CATEGORY_GUIDANCE:
        raise KeyError(f"unknown category {category!r}")
    lang = LANGUAGE_NAMES.get(language, language)
    user = (
        f"Category: {category}\n"
        f"What this category looks like: {CATEGORY_GUIDANCE[category]}\n"
        f"Write in: {lang}\n\n"
        f"Example message:\n{seed_text}\n\n"
        f"Write {n} new, varied messages of this category. Vary sender style, length, "
        "spelling, emoji use and details. Keep the same intent as the example but do not "
        f"copy it. Output exactly {n} lines."
    )
    return [{"role": "system", "content": GENERATE_SYSTEM}, {"role": "user", "content": user}]


LABEL_SYSTEM = f"""You label phone messages from Pakistan for a scam detector.
Reply with exactly one line in the format: label|scam_type|flags

label: safe | promo | scam
scam_type (only if label is scam, otherwise none): {", ".join(SCAM_TYPES)}
flags: comma-separated, in this order, from {", ".join(FLAG_ORDER)}; or none

Rules:
- A genuine OTP that says not to share it is safe, not code_theft.
- Official BISP messages from 8171 with no fee are safe.
- Legitimate marketing is promo. Anything asking for a code, PIN, fee or transfer
  under false pretences is scam.
- has_link if the message contains any URL. urgency if it pressures to act now.
- claims_official if it claims to be a bank, government, telco or company.
Reply with the line only."""

FEW_SHOT: tuple[tuple[str, str], ...] = (
    (
        "Assalam o alaikum, ghalti se aap ke number par 6 digit code chala gaya hai, "
        "please jaldi wapis bhej dein.",
        "scam|code_theft|asks_for_code,urgency",
    ),
    (
        "Your FictionBank OTP is 000000. Do not share this code with anyone. "
        "FictionBank will never ask for it.",
        "safe|none|claims_official",
    ),
    (
        "Mubarak ho! Aap ka BISP ki 25000 ki raqam mangwane ke liye 500 fee is number "
        "par bhejein 0300-0000000",
        "scam|fake_govt_aid|asks_for_money,claims_official",
    ),
    (
        "FictionTel: Sirf Rs.99 mein 5GB weekly bundle. Dial *000# abhi!",
        "promo|none|claims_official",
    ),
    ("Ammi, main ghar 8 baje tak aa jaunga, khana rakh dena.", "safe|none|none"),
)


def label_prompt(text: str) -> list[dict[str, str]]:
    messages: list[dict[str, str]] = [{"role": "system", "content": LABEL_SYSTEM}]
    for example, line in FEW_SHOT:
        messages.append({"role": "user", "content": f"Message:\n{example}"})
        messages.append({"role": "assistant", "content": line})
    messages.append({"role": "user", "content": f"Message:\n{text}"})
    return messages
