import { useEffect, useMemo, useState } from "react";
import apiClient from "../api/apiClient";
import MerchantSelector from "../components/MerchantSelector";
import PaymentForm from "../components/PaymentForm";
import ResultCard from "../components/ResultCard";
import SectionCard from "../components/SectionCard";

function Dashboard() {
    const [merchants, setMerchants] = useState([]);
    const [selectedMerchantId, setSelectedMerchantId] = useState("");

    const [paymentResult, setPaymentResult] = useState(null);
    const [ledgerState, setLedgerState] = useState(null);
    const [settlement, setSettlement] = useState(null);
    const [fraudChecks, setFraudChecks] = useState(null);
    const [notifications, setNotifications] = useState(null);

    const [loadingMerchants, setLoadingMerchants] = useState(false);
    const [error, setError] = useState("");

    const selectedMerchant = useMemo(() => {
        return merchants.find((merchant) => merchant.id === selectedMerchantId);
    }, [merchants, selectedMerchantId]);

    useEffect(() => {
        loadMerchants();
    }, []);

    async function loadMerchants() {
        setLoadingMerchants(true);
        setError("");

        try {
            const response = await apiClient.get("/api/merchants");
            setMerchants(response.data);

            if (response.data.length > 0 && !selectedMerchantId) {
                setSelectedMerchantId(response.data[0].id);
            }
        } catch (exception) {
            setError(buildErrorMessage(exception));
        } finally {
            setLoadingMerchants(false);
        }
    }

    async function initiatePayment({ apiKey, ipAddress, payload }) {
        setError("");
        setPaymentResult(null);
        setLedgerState(null);
        setSettlement(null);
        setFraudChecks(null);
        setNotifications(null);

        try {
            const paymentResponse = await apiClient.post(
                "/api/payments/initiate",
                payload,
                {
                    headers: {
                        "X-API-Key": apiKey,
                        "X-Forwarded-For": ipAddress,
                    },
                }
            );

            setPaymentResult(paymentResponse.data);
            await loadPaymentDetails(paymentResponse.data.id);
        } catch (exception) {
            setError(buildErrorMessage(exception));
        }
    }

    async function loadPaymentDetails(paymentId) {
        const requests = [
            apiClient.get(`/api/ledger/payments/${paymentId}/state`),
            apiClient.get(`/api/fraud/payments/${paymentId}`),
            apiClient.get(`/api/notifications/payments/${paymentId}`),
            apiClient.get(`/api/settlements/payments/${paymentId}`),
        ];

        const [ledgerResult, fraudResult, notificationResult, settlementResult] =
            await Promise.allSettled(requests);

        if (ledgerResult.status === "fulfilled") {
            setLedgerState(ledgerResult.value.data);
        } else {
            setLedgerState({
                message: "Ledger state could not be loaded yet.",
            });
        }

        if (fraudResult.status === "fulfilled") {
            setFraudChecks(fraudResult.value.data);
        } else {
            setFraudChecks({
                message: "Fraud checks could not be loaded yet.",
            });
        }

        if (notificationResult.status === "fulfilled") {
            setNotifications(notificationResult.value.data);
        } else {
            setNotifications({
                message: "Notifications could not be loaded yet.",
            });
        }

        if (settlementResult.status === "fulfilled") {
            setSettlement(settlementResult.value.data);
        } else {
            setSettlement({
                message:
                    "Settlement not created. This is expected for FAILED or fraud-rejected payments.",
            });
        }
    }

    function buildErrorMessage(exception) {
        if (exception.response?.data?.message) {
            return exception.response.data.message;
        }

        if (exception.response?.data?.error) {
            return exception.response.data.error;
        }

        return exception.message || "Unexpected error occurred.";
    }

    return (
        <div className="dashboard-page">
            <header className="hero">
                <div>
                    <p className="eyebrow">PayCore Connect</p>
                    <h1>Merchant Payment Dashboard</h1>
                    <p>
                        Trigger payments through the API Gateway and inspect ledger,
                        settlement, fraud, and notification outputs in one place.
                    </p>
                </div>

                <button className="secondary-button" onClick={loadMerchants}>
                    {loadingMerchants ? "Loading..." : "Refresh Merchants"}
                </button>
            </header>

            {error && <div className="error-banner">{error}</div>}

            <div className="layout-grid">
                <SectionCard title="Merchant Context">
                    <MerchantSelector
                        merchants={merchants}
                        selectedMerchantId={selectedMerchantId}
                        onSelectMerchant={setSelectedMerchantId}
                    />
                </SectionCard>

                <SectionCard title="Create Payment">
                    <PaymentForm
                        selectedMerchant={selectedMerchant}
                        onPaymentCreated={initiatePayment}
                    />
                </SectionCard>
            </div>

            <div className="result-grid">
                <ResultCard title="Payment Result" data={paymentResult} />
                <ResultCard title="Ledger State" data={ledgerState} />
                <ResultCard title="Settlement" data={settlement} />
                <ResultCard title="Fraud Checks" data={fraudChecks} />
                <ResultCard title="Notifications" data={notifications} />
            </div>
        </div>
    );
}

export default Dashboard;