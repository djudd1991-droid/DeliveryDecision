# Delivery Decision 💰

Smart order decision-making for DoorDash and Uber Eats drivers.

Delivery Decision analyzes delivery offers in real-time and gives you instant **TAKE / CONSIDER / PASS** recommendations based on your personal metrics: gas cost, target hourly rate, preferred pay-per-mile, and more.

## Features ✨

- **Real-Time Offer Analysis** – Reads offer details from DoorDash/Uber Eats
- **Smart Decision Engine** – Calculates pay-per-mile, fuel costs, hourly rates
- **Live Tracking** – Automatic GPS mileage tracking while on a dash
- **Comprehensive History** – Track every delivery with earnings and miles
- **Tax Reports** – Export data for tax season (CSV & PDF)
- **Multi-Platform** – Works with both DoorDash and Uber Eats

## Quick Start

### Requirements
- Android 8.0+ (API 26)
- DoorDash or Uber Eats driver app installed
- Location permission enabled

### Installation

1. Download the APK from Releases
2. Install on your phone
3. Grant location permission
4. Enable Accessibility Service in Settings

## How It Works

The app calculates:
- **Pay-per-Mile** = Offer Pay / Distance
- **Fuel Cost** = Distance / MPG × Gas Price
- **After-Fuel Profit** = Offer Pay - Fuel Cost
- **Estimated Hourly** = Offer Pay / (Estimated Time ÷ 60)

**Decision Rules:**
- 🟢 **TAKE** – Pay/mi ≥ preferred AND hourly ≥ target
- 🟡 **CONSIDER** – Pay/mi ≥ minimum 
- 🔴 **PASS** – Below minimum

## Tabs

- **Home** – Current session stats
- **History** – All past deliveries
- **Reports** – Daily/weekly/monthly summaries
- **Accounts** – Earnings by platform
- **Settings** – Configure metrics
- **Taxes** – Export for tax season

## License

MIT License - See LICENSE file

## Support

Have questions? Open an issue on GitHub!

---

Made with ❤️ for delivery drivers.
