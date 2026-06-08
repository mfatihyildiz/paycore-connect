import { useState } from "react";

const initialForm = {
    amount: "1000.00",
    currency: "TRY",
    orderId: "",
    cardToken: "card_token_1234567890123456",
    providerType: "MOCK_BANK",
    ipAddress: "192.168.1.50",
};

function PaymentForm({ selectedMerchant, onPaymentCreated }) {
    const [form, setForm] = useState(initialForm);
    const [loading, setLoading] = useState(false);

    function updateField(field, value) {
        setForm((previous) => ({
            ...previous,
            [field]: value,
        }));
    }

    function generateOrderId() {
        updateField("orderId", `ORDER-UI-${Date.now()}`);
    }

    async function handleSubmit(event) {
        event.preventDefault();

        if (!selectedMerchant) {
            alert("Please select a merchant first.");
            return;
        }

        if (!form.orderId.trim()) {
            alert("Order ID is required.");
            return;
        }

        setLoading(true);

        try {
            await onPaymentCreated({
                apiKey: selectedMerchant.apiKey,
                ipAddress: form.ipAddress,
                payload: {
                    amount: Number(form.amount),
                    currency: form.currency,
                    orderId: form.orderId,
                    cardToken: form.cardToken,
                    providerType: form.providerType,
                },
            });
        } finally {
            setLoading(false);
        }
    }

    return (
        <form className="payment-form" onSubmit={handleSubmit}>
            <div className="form-grid">
                <div>
                    <label>Amount</label>
                    <input
                        type="number"
                        step="0.01"
                        value={form.amount}
                        onChange={(event) => updateField("amount", event.target.value)}
                    />
                </div>

                <div>
                    <label>Currency</label>
                    <input
                        value={form.currency}
                        onChange={(event) => updateField("currency", event.target.value)}
                    />
                </div>

                <div>
                    <label>Order ID</label>
                    <div className="input-with-button">
                        <input
                            value={form.orderId}
                            onChange={(event) => updateField("orderId", event.target.value)}
                            placeholder="ORDER-UI-1001"
                        />
                        <button type="button" onClick={generateOrderId}>
                            Generate
                        </button>
                    </div>
                </div>

                <div>
                    <label>Provider Type</label>
                    <select
                        value={form.providerType}
                        onChange={(event) => updateField("providerType", event.target.value)}
                    >
                        <option value="MOCK_BANK">MOCK_BANK</option>
                        <option value="LEGACY_BANK_SOAP">LEGACY_BANK_SOAP</option>
                    </select>
                </div>

                <div>
                    <label>Card Token</label>
                    <input
                        value={form.cardToken}
                        onChange={(event) => updateField("cardToken", event.target.value)}
                    />
                </div>

                <div>
                    <label>Client IP</label>
                    <input
                        value={form.ipAddress}
                        onChange={(event) => updateField("ipAddress", event.target.value)}
                    />
                </div>
            </div>

            <button className="primary-button" type="submit" disabled={loading}>
                {loading ? "Processing..." : "Initiate Payment"}
            </button>
        </form>
    );
}

export default PaymentForm;