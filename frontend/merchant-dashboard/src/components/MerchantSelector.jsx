function MerchantSelector({ merchants, selectedMerchantId, onSelectMerchant }) {
    const selectedMerchant = merchants.find(
        (merchant) => merchant.id === selectedMerchantId
    );

    return (
        <div className="merchant-selector">
            <label>Merchant</label>

            <select
                value={selectedMerchantId || ""}
                onChange={(event) => onSelectMerchant(event.target.value)}
            >
                <option value="">Select merchant</option>

                {merchants.map((merchant) => (
                    <option key={merchant.id} value={merchant.id}>
                        {merchant.name} - {merchant.status}
                    </option>
                ))}
            </select>

            {selectedMerchant && (
                <div className="api-key-box">
                    <span>API Key</span>
                    <code>{selectedMerchant.apiKey}</code>
                </div>
            )}
        </div>
    );
}

export default MerchantSelector;