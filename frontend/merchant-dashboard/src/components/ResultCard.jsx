function ResultCard({ title, data }) {
    return (
        <div className="result-card">
            <h3>{title}</h3>

            {data ? (
                <pre>{JSON.stringify(data, null, 2)}</pre>
            ) : (
                <p className="muted">No data yet.</p>
            )}
        </div>
    );
}

export default ResultCard;