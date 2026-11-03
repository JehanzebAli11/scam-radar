from __future__ import annotations

import pytest

from scamradar.anonymise import Anonymiser, anonymise, load_names


@pytest.mark.parametrize(
    "text",
    [
        "Call 0300-1234567 now",
        "Call 03001234567 now",
        "Call 0300 1234567 now",
        "Call 0321 123 4567 now",
        "Call +92 300 1234567 now",
        "Call +923001234567 now",
        "Call +92-300-1234567 now",
        "Call 0092 300 1234567 now",
        "Call 92 300 1234567 now",
        "Call +92 (0) 300 1234567 now",
        "Call 021-34567890 now",
        "Call (042) 35761234 now",
        "Call +92 21 34567890 now",
        "Call +92-42-3576-1234 now",
    ],
)
def test_phone_formats(text: str) -> None:
    assert anonymise(text) == "Call <PHONE> now"


@pytest.mark.parametrize(
    "text",
    [
        "BISP 8171 se paigham",  # official short code must survive
        "Your OTP is 482913",  # OTP digits are a signal, not PII
        "Rs. 25,000 ki qist",
        "Dial *123# for balance",
        "Order 1234567 shipped",  # 7 digits, not a phone
        "Tracking FX00000000",
        "Ref 2024-01-15",
    ],
)
def test_non_pii_numbers_untouched(text: str) -> None:
    assert anonymise(text) == text


def test_phone_with_punctuation_boundary() -> None:
    assert anonymise("(0300-1234567), ya 0333-7654321.") == "(<PHONE>), ya <PHONE>."


def test_cnic_dashed_and_plain() -> None:
    assert anonymise("CNIC 35202-1234567-1 bhejo") == "CNIC <CNIC> bhejo"
    assert anonymise("CNIC 3520212345671 bhejo") == "CNIC <CNIC> bhejo"


def test_cnic_not_confused_with_phone() -> None:
    assert anonymise("35202-1234567-1 aur 0300-1234567") == "<CNIC> aur <PHONE>"


@pytest.mark.parametrize(
    "text",
    [
        "IBAN PK36SCBL0000001123456702 me",
        "IBAN PK36 SCBL 0000 0011 2345 6702 me",
        "IBAN pk36scbl0000001123456702 me",
        "IBAN PK36-SCBL-0000-0011-2345-6702 me",
    ],
)
def test_iban(text: str) -> None:
    assert anonymise(text) == "IBAN <IBAN> me"


def test_email() -> None:
    assert anonymise("mail ali.khan+x@mail.example.com now") == "mail <EMAIL> now"


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        (
            "Visit https://bisp-gov.pk-verify.com/claim?id=99 now",
            "Visit https://bisp-gov.pk-verify.com/<PATH> now",
        ),
        ("Go http://fake.example/a/b/c.", "Go http://fake.example/<PATH>."),
        ("open bit.ly/abc123", "open bit.ly/<PATH>"),
        ("open www.zeta-pay.example/login?u=1", "open www.zeta-pay.example/<PATH>"),
        ("open https://example.pk", "open https://example.pk"),
        ("open https://example.pk/", "open https://example.pk/"),
        ("site www.example.com today", "site www.example.com today"),
        ("http://evil.example:8080/x", "http://evil.example:8080/<PATH>"),
    ],
)
def test_url_keeps_domain_strips_path(text: str, expected: str) -> None:
    assert anonymise(text) == expected


def test_plain_words_with_dots_untouched() -> None:
    text = "e.g. Rs.500 or Mr.Ali, U.S. visit"
    assert anonymise(text) == text


def test_email_domain_not_treated_as_url() -> None:
    assert anonymise("contact help@bank.example.com/") == "contact <EMAIL>/"


def test_names_list_case_insensitive_word_boundaries() -> None:
    anon = Anonymiser(names=("Ahmed", "Sara Khan", "  "))
    assert (
        anon("ahmed bhai aur Sara Khan, Ahmedabad nahi") == "<NAME> bhai aur <NAME>, Ahmedabad nahi"
    )


def test_names_in_urdu_script() -> None:
    assert anonymise("سلام احمد بھائی", names=["احمد"]) == "سلام <NAME> بھائی"


def test_numbered_placeholders_are_consistent() -> None:
    text = "0300-1234567 aur 03001234567 aur 0321 7654321, CNIC 35202-1234567-1"
    assert anonymise(text, numbered=True) == "<PHONE_1> aur <PHONE_1> aur <PHONE_2>, CNIC <CNIC_1>"


def test_deterministic_and_idempotent() -> None:
    text = "Call 0300-1234567, CNIC 35202-1234567-1, http://x.example/p, a@b.example"
    once = anonymise(text)
    assert once == anonymise(text)
    assert anonymise(once) == once


def test_mixed_message() -> None:
    text = (
        "Mubarak ho! BISP 8171: raqam ke liye 0300-1234567 par CNIC 35202-1234567-1 bhejein "
        "ya https://bisp.example/claim?x=1 visit karein. Ahmed"
    )
    assert anonymise(text, names=["Ahmed"]) == (
        "Mubarak ho! BISP 8171: raqam ke liye <PHONE> par CNIC <CNIC> bhejein "
        "ya https://bisp.example/<PATH> visit karein. <NAME>"
    )


def test_load_names(tmp_path) -> None:
    p = tmp_path / "names.txt"
    p.write_text("# comment\nAhmed\n\n  Sara  \n", encoding="utf-8")
    assert load_names(str(p)) == ("Ahmed", "Sara")
