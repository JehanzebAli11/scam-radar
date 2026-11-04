package io.github.jehanzebali11.scamradar.ui

/** Fictional sample messages behind the "Try an example" chips (same style as data/seeds). */
data class Example(val label: String, val text: String)

val EXAMPLES =
    listOf(
        Example(
            "Code scam",
            "Assalam o alaikum bhai, ghalti se mera 6 digit code aap ke number par chala gaya hai. " +
                "Please woh code mujhe wapis bhej dein, bohat zaroori hai.",
        ),
        Example(
            "Bank OTP",
            "Your FictionBank OTP is 000000 for a transaction of Rs 5,000. Do not share this code with anyone. " +
                "FictionBank will never ask for your OTP.",
        ),
        Example(
            "Parcel link",
            "Your parcel could not be delivered due to an incomplete address. Update your details here: " +
                "http://parcel-redelivery.example/pk",
        ),
        Example(
            "Prize",
            "Mubarak ho! Aap ka number Jeeto Inaam Show ki lucky draw mein 5 lakh aur ek bike jeet chuka hai. " +
                "Inaam lene ke liye tax 3500 is account mein jama karwayen.",
        ),
        Example("Promo", "NovaTel offer! 5GB weekly bundle sirf Rs 99. Details: https://bit.ly/nt-offer"),
    )
