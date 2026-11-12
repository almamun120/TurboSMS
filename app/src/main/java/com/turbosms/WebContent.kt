package com.turbosms

object WebContent {

    /** PIN লগইন পেজ — `$` চিহ্ন ব্যবহার করা যাবে না (Kotlin raw string) */
    fun getLoginPage(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Turbo SMS - Login</title>
<style>
:root{--bg1:#0f172a;--bg2:#1e1b4b;--card:rgba(255,255,255,.08);--text:#e2e8f0;--muted:#94a3b8;--p1:#6366f1;--p2:#06b6d4;--err:#f87171}
*{box-sizing:border-box;margin:0;padding:0}
body{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px;font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;color:var(--text);background:radial-gradient(1200px 600px at 10% -10%,#312e81 0,transparent 60%),radial-gradient(900px 500px at 110% 110%,#0e7490 0,transparent 55%),linear-gradient(160deg,var(--bg1),var(--bg2))}
.card{width:100%;max-width:380px;background:var(--card);border:1px solid rgba(255,255,255,.14);backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px);border-radius:24px;padding:32px 26px;box-shadow:0 24px 60px rgba(0,0,0,.45);text-align:center}
.logo{width:64px;height:64px;border-radius:20px;margin:0 auto 16px;display:flex;align-items:center;justify-content:center;font-size:30px;background:linear-gradient(135deg,var(--p1),var(--p2));box-shadow:0 10px 30px rgba(99,102,241,.5)}
h1{font-size:24px;font-weight:700;letter-spacing:.3px}
p{color:var(--muted);font-size:14px;margin:8px 0 22px;line-height:1.5}
input{width:100%;padding:16px;font-size:26px;letter-spacing:10px;text-align:center;color:var(--text);background:rgba(15,23,42,.6);border:1.5px solid rgba(255,255,255,.18);border-radius:14px;outline:none;transition:.2s}
input:focus{border-color:var(--p1);box-shadow:0 0 0 4px rgba(99,102,241,.25)}
button{width:100%;margin-top:16px;padding:15px;font-size:16px;font-weight:600;color:#fff;border:0;border-radius:14px;cursor:pointer;background:linear-gradient(135deg,var(--p1),var(--p2));transition:transform .15s,opacity .15s}
button:active{transform:scale(.98)}button:disabled{opacity:.6}
.err{min-height:20px;margin-top:12px;font-size:13px;color:var(--err)}
.hint{margin-top:18px;font-size:12px;color:var(--muted)}
</style>
</head>
<body>
<div class="card">
  <div class="logo">&#9993;</div>
  <h1>Turbo SMS</h1>
  <p>Enter the 6-digit PIN shown in the Turbo SMS app on your phone.</p>
  <input id="pin" type="password" inputmode="numeric" maxlength="12" autocomplete="one-time-code" placeholder="&bull;&bull;&bull;&bull;&bull;&bull;" autofocus>
  <button id="btn" onclick="login()">Unlock</button>
  <div class="err" id="err"></div>
  <div class="hint">Only people with the PIN can send SMS from this phone.</div>
</div>
<script>
var pinEl=document.getElementById('pin'),errEl=document.getElementById('err'),btn=document.getElementById('btn');
pinEl.addEventListener('keydown',function(e){if(e.key==='Enter')login();});
function login(){
  var pin=pinEl.value.trim();
  if(!pin){errEl.textContent='Enter your PIN';return;}
  btn.disabled=true;errEl.textContent='';
  fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({pin:pin})})
   .then(function(r){return r.json();})
   .then(function(d){ if(d.success){location.reload();} else {errEl.textContent=d.error||'Wrong PIN';btn.disabled=false;pinEl.select();} })
   .catch(function(){errEl.textContent='Cannot reach the phone';btn.disabled=false;});
}
</script>
</body>
</html>
""".trimIndent()

    fun getMainPage(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Turbo SMS - Bulk SMS Sender</title>
    <link rel="stylesheet" href="/static/style.css">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
</head>
<body>
    <div class="container">
        <header class="header">
            <div class="logo">
                <i class="fas fa-paper-plane"></i>
                <h1>Turbo SMS</h1>
            </div>
            <div class="header-actions">
                <button class="theme-toggle" id="themeToggle" title="Toggle Dark Mode">
                    <i class="fas fa-moon" id="themeIcon"></i>
                </button>
                <div class="status-indicator" id="connectionStatus">
                    <span class="status-dot"></span>
                    <span>Connected</span>
                </div>
            </div>
        </header>

        <main class="main-content">
            <!-- SIM Configuration Section -->
            <section class="card sim-config-section">
                <h2><i class="fas fa-sim-card"></i> SIM Configuration</h2>
                
                <div class="sim-info-banner" id="simInfoBanner">
                    <i class="fas fa-info-circle"></i>
                    <span>Loading SIM information...</span>
                </div>
                
                <div class="sim-selection-mode">
                    <label class="radio-container">
                        <input type="radio" name="simMode" id="simModeSingle" value="single" checked>
                        <span class="radio-checkmark"></span>
                        <span>Single SIM</span>
                    </label>
                    <label class="radio-container">
                        <input type="radio" name="simMode" id="simModeRatio" value="ratio">
                        <span class="radio-checkmark"></span>
                        <span>SIM Ratio (Alternate)</span>
                    </label>
                </div>

                <div id="singleSimConfig" class="sim-config-panel">
                    <p class="config-hint">Select which SIM card to use for sending SMS:</p>
                    <div class="sim-buttons">
                        <button class="sim-btn active" id="simBtn1" data-sim="1">
                            <i class="fas fa-sim-card"></i>
                            <span class="sim-label">SIM 1</span>
                            <span class="sim-carrier" id="sim1Carrier">Default</span>
                        </button>
                        <button class="sim-btn" id="simBtn2" data-sim="2">
                            <i class="fas fa-sim-card"></i>
                            <span class="sim-label">SIM 2</span>
                            <span class="sim-carrier" id="sim2Carrier">Default</span>
                        </button>
                    </div>
                    <div class="selected-sim-info">
                        Currently selected: <strong id="currentSimDisplay">SIM 1</strong>
                    </div>
                </div>

                <div id="ratioSimConfig" class="sim-config-panel hidden">
                    <p class="config-hint">Set the ratio for alternating between SIMs:</p>
                    <div class="ratio-inputs">
                        <div class="ratio-item">
                            <label><i class="fas fa-sim-card"></i> SIM 1</label>
                            <input type="number" id="sim1Ratio" value="1" min="0" max="10">
                            <span class="ratio-label">messages</span>
                        </div>
                        <div class="ratio-divider">then</div>
                        <div class="ratio-item">
                            <label><i class="fas fa-sim-card"></i> SIM 2</label>
                            <input type="number" id="sim2Ratio" value="1" min="0" max="10">
                            <span class="ratio-label">messages</span>
                        </div>
                    </div>
                    <div class="ratio-visualization">
                        <span>Send pattern: </span>
                        <span id="ratioPattern" class="pattern-badges"></span>
                    </div>
                </div>
            </section>

            <!-- File Upload Section -->
            <section class="card file-upload-section">
                <h2><i class="fas fa-file-upload"></i> Import Data</h2>
                <div class="file-upload-area" id="dropZone">
                    <i class="fas fa-cloud-upload-alt"></i>
                    <p>Drag & drop file here or click to browse</p>
                    <span class="file-types">Supports: .xlsx, .xls, .xlsm, .txt, .csv</span>
                    <input type="file" id="fileInput" accept=".xlsx,.xls,.xlsm,.txt,.csv" hidden>
                </div>
                
                <div class="file-options">
                    <div class="header-option">
                        <label class="checkbox-container">
                            <input type="checkbox" id="hasHeader">
                            <span class="checkmark"></span>
                            <span class="checkbox-label">
                                <i class="fas fa-heading"></i> First row is header
                            </span>
                        </label>
                        <span class="option-hint">Skip the first row when sending SMS</span>
                    </div>
                    
                    <div class="separator-config">
                        <label>Text file separator:</label>
                        <select id="separator">
                            <option value="\t">Tab</option>
                            <option value=",">Comma (,)</option>
                            <option value=";">Semicolon (;)</option>
                            <option value="|">Pipe (|)</option>
                        </select>
                    </div>
                </div>
                
                <div class="sheet-selector hidden" id="sheetSelector">
                    <label><i class="fas fa-layer-group"></i> Select Sheet:</label>
                    <div class="sheet-tabs" id="sheetTabs"></div>
                </div>
                
                <!-- UPDATED: File Preview Section -->
                <div id="filePreview" class="file-preview hidden">
                    <h3><i class="fas fa-table"></i> Data Preview</h3>
                    <div class="preview-info">
                        <span id="rowCount"><i class="fas fa-list"></i> 0 data rows</span>
                        <span id="colCount"><i class="fas fa-columns"></i> 0 columns</span>
                        <span id="sheetName" class="sheet-badge hidden"></span>
                        <span id="headerBadge" class="header-badge hidden">
                            <i class="fas fa-heading"></i> First row is header
                        </span>
                    </div>
                    
                    <div class="preview-table-wrapper">
                        <div class="preview-table-container" id="previewTableContainer">
                            <table id="previewTable"></table>
                        </div>
                        <div class="preview-footer">
                            <span id="previewStats">Showing all rows</span>
                            <span class="scroll-hint">
                                <i class="fas fa-arrows-alt"></i> Scroll to see more
                            </span>
                        </div>
                    </div>
                    
                    <div class="available-variables">
                        <h4><i class="fas fa-code"></i> Available Column Variables (click to insert)</h4>
                        <div id="columnVariables" class="variable-tags"></div>
                    </div>
                </div>
            </section>

            <!-- Row Range Section -->
            <section class="card row-range-section">
                <h2><i class="fas fa-filter"></i> Row Range Filter</h2>
                <div class="form-group">
                    <label for="rowRange">
                        Specify rows to send (leave empty for all rows)
                    </label>
                    <input type="text" id="rowRange" placeholder="Examples: 1,2,6 or 5-42 or 1-10,15,20-30">
                    <div class="range-hints">
                        <span class="hint-item"><code>1,2,6</code> = rows 1, 2, and 6</span>
                        <span class="hint-item"><code>5-42</code> = rows 5 to 42</span>
                        <span class="hint-item"><code>1-10,15,20-30</code> = mixed</span>
                    </div>
                    <div class="range-info" id="rangeInfo"></div>
                </div>
            </section>

            <!-- SMS Composer Section -->
            <section class="card sms-composer">
                <h2><i class="fas fa-sms"></i> Compose Message</h2>
                
                <div class="form-group">
                    <label for="numbers">
                        <i class="fas fa-phone"></i> Phone Numbers
                        <span class="hint">Use column variable like {col-1} or enter manually</span>
                    </label>
                    <textarea id="numbers" placeholder="e.g., +8801712345678 or use {col-1}"></textarea>
                </div>

                <div class="form-group">
                    <label for="message">
                        <i class="fas fa-comment"></i> Message
                        <span class="hint">Unicode/Bangla supported</span>
                    </label>
                    <textarea id="message" placeholder="Type your message here... বাংলা সাপোর্টেড"></textarea>
                    <div class="message-info">
                        <div class="char-counter">
                            <span id="charCount">0</span>/<span id="maxChars">160</span> chars
                            <span class="encoding-badge" id="encodingBadge">GSM-7</span>
                        </div>
                        <div class="part-counter">
                            <span id="totalParts">1</span> SMS part(s)
                        </div>
                    </div>
                </div>

                <div class="variables-panel">
                    <h4><i class="fas fa-code"></i> Insert Variables</h4>
                    <div class="variable-buttons">
                        <button class="var-btn" data-var="{col-1}">{col-1}</button>
                        <button class="var-btn" data-var="{col-2}">{col-2}</button>
                        <button class="var-btn" data-var="{col-3}">{col-3}</button>
                        <button class="var-btn" data-var="{timestamp}">Timestamp</button>
                        <button class="var-btn" data-var="{date}">Date</button>
                        <button class="var-btn" data-var="{time}">Time</button>
                        <button class="var-btn" data-var="{random}">Random</button>
                        <button class="var-btn" data-var="{index}">Row Index</button>
                    </div>
                    <div id="dynamicVariables" class="dynamic-variables"></div>
                </div>

                <div class="form-group interval-group">
                    <label>
                        <i class="fas fa-stopwatch"></i> Delay Between SMS
                        <span class="interval-value" id="intervalValue">1 second</span>
                    </label>
                    <input type="range" id="intervalSlider" min="1" max="60" value="1" class="slider">
                    <div class="slider-labels">
                        <span>1s</span>
                        <span>30s</span>
                        <span>60s</span>
                    </div>
                </div>

                <div class="control-buttons">
                    <button id="btnPrepare" class="btn btn-primary">
                        <i class="fas fa-check"></i> Prepare Queue
                    </button>
                    <button id="btnStart" class="btn btn-success" disabled>
                        <i class="fas fa-play"></i> Start Sending
                    </button>
                    <button id="btnPause" class="btn btn-warning" disabled>
                        <i class="fas fa-pause"></i> Pause
                    </button>
                    <button id="btnStop" class="btn btn-danger" disabled>
                        <i class="fas fa-stop"></i> Stop
                    </button>
                    <button id="btnReset" class="btn btn-secondary">
                        <i class="fas fa-redo"></i> Reset
                    </button>
                </div>
            </section>

            <!-- Progress Section -->
            <section class="card progress-section">
                <h2><i class="fas fa-tasks"></i> Progress</h2>
                
                <div class="progress-stats">
                    <div class="stat-box">
                        <span class="stat-value" id="statTotal">0</span>
                        <span class="stat-label">Total</span>
                    </div>
                    <div class="stat-box success">
                        <span class="stat-value" id="statSent">0</span>
                        <span class="stat-label">Sent</span>
                    </div>
                    <div class="stat-box error">
                        <span class="stat-value" id="statFailed">0</span>
                        <span class="stat-label">Failed</span>
                    </div>
                    <div class="stat-box pending">
                        <span class="stat-value" id="statPending">0</span>
                        <span class="stat-label">Pending</span>
                    </div>
                </div>

                <div class="progress-bar-container">
                    <div class="progress-bar" id="progressBar">
                        <span class="progress-text" id="progressText">0%</span>
                    </div>
                </div>
                
                <div class="sim-usage-stats">
                    <div class="sim-usage-item">
                        <i class="fas fa-sim-card"></i>
                        <span>SIM 1 sent:</span>
                        <strong id="sim1SentCount">0</strong>
                    </div>
                    <div class="sim-usage-item">
                        <i class="fas fa-sim-card"></i>
                        <span>SIM 2 sent:</span>
                        <strong id="sim2SentCount">0</strong>
                    </div>
                </div>
            </section>

            <!-- Logs Section -->
            <section class="card logs-section">
                <div class="logs-header">
                    <h2><i class="fas fa-list-alt"></i> Response Log</h2>
                    <button id="btnExport" class="btn btn-outline">
                        <i class="fas fa-download"></i> Export CSV
                    </button>
                </div>
                <div class="logs-container" id="logsContainer">
                    <div class="log-placeholder">
                        <i class="fas fa-inbox"></i>
                        <p>Logs will appear here when sending starts</p>
                    </div>
                </div>
            </section>
            <!-- About Section -->
            <section class="card about-section">
                <h2><i class="fas fa-info-circle"></i> About Developer</h2>
                <div class="about-content">
                    <p><strong>Developer:</strong> Abdullah Al Mamun</p>
                    <p><strong>Address:</strong> Dhaka, Bangladesh</p>
                    <p><strong>Email:</strong> <a href="mailto:mamun995599@gmail.com">mamun995599@gmail.com</a></p>
                    <p><strong>Phone:</strong> <a href="tel:+8801945120109">+8801945120109</a></p>
                </div>
            </section>
        </main>

        <footer class="footer">
            <p>Turbo SMS v2.0 | Unicode & Dual SIM Support</p>
        </footer>
    </div>

    <script src="/static/app.js"></script>
</body>
</html>
""".trimIndent()

    fun getCss(): String = """
:root {
    --primary: #6366f1;
    --primary-dark: #4f46e5;
    --primary-light: rgba(99, 102, 241, 0.15);
    --success: #22c55e;
    --success-light: rgba(34, 197, 94, 0.15);
    --warning: #f59e0b;
    --warning-light: rgba(245, 158, 11, 0.15);
    --error: #ef4444;
    --error-light: rgba(239, 68, 68, 0.15);
    --background: #f8fafc;
    --card-bg: #ffffff;
    --text-primary: #1e293b;
    --text-secondary: #64748b;
    --border: #e2e8f0;
    --shadow: 0 4px 20px rgba(0,0,0,0.08);
    --radius: 16px;
    --input-bg: #ffffff;
    --hover-bg: #f1f5f9;
    --code-bg: #f1f5f9;
}

[data-theme="dark"] {
    --primary: #818cf8;
    --primary-dark: #6366f1;
    --primary-light: rgba(129, 140, 248, 0.2);
    --success: #4ade80;
    --success-light: rgba(74, 222, 128, 0.2);
    --warning: #fbbf24;
    --warning-light: rgba(251, 191, 36, 0.2);
    --error: #f87171;
    --error-light: rgba(248, 113, 113, 0.2);
    --background: #0f172a;
    --card-bg: #1e293b;
    --text-primary: #f1f5f9;
    --text-secondary: #94a3b8;
    --border: #334155;
    --shadow: 0 4px 20px rgba(0,0,0,0.4);
    --input-bg: #334155;
    --hover-bg: #334155;
    --code-bg: #334155;
}

* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
    background: var(--background);
    color: var(--text-primary);
    line-height: 1.6;
}

.container {
    max-width: 1000px;
    margin: 0 auto;
    padding: 20px;
}

/* Header */
.header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 20px 0;
    margin-bottom: 20px;
}

.logo {
    display: flex;
    align-items: center;
    gap: 12px;
}

.logo i {
    font-size: 2rem;
    color: var(--primary);
}

.logo h1 {
    font-size: 1.8rem;
    font-weight: 700;
    color: var(--primary);
}

.header-actions {
    display: flex;
    align-items: center;
    gap: 15px;
}

.theme-toggle {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    border: 2px solid var(--border);
    background: var(--card-bg);
    color: var(--text-primary);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.2rem;
    transition: all 0.3s;
}

.theme-toggle:hover {
    background: var(--primary);
    color: white;
    border-color: var(--primary);
}

.status-indicator {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    background: var(--success-light);
    border-radius: 20px;
    font-size: 0.9rem;
    font-weight: 500;
    color: var(--success);
}

.status-dot {
    width: 10px;
    height: 10px;
    background: var(--success);
    border-radius: 50%;
    animation: pulse 2s infinite;
}

@keyframes pulse {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.5; transform: scale(0.9); }
}

/* Cards */
.card {
    background: var(--card-bg);
    border-radius: var(--radius);
    padding: 24px;
    margin-bottom: 20px;
    box-shadow: var(--shadow);
    border: 1px solid var(--border);
}

.card h2 {
    font-size: 1.2rem;
    font-weight: 600;
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    gap: 10px;
    color: var(--text-primary);
}

.card h2 i {
    color: var(--primary);
}

/* SIM Configuration */
.sim-config-section {
    border: 2px solid var(--primary);
    background: linear-gradient(135deg, var(--card-bg) 0%, var(--primary-light) 100%);
}

.sim-info-banner {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 16px;
    background: var(--primary-light);
    border-radius: 10px;
    margin-bottom: 20px;
    font-size: 0.9rem;
    color: var(--primary);
}

.sim-info-banner.success {
    background: var(--success-light);
    color: var(--success);
}

.sim-selection-mode {
    display: flex;
    gap: 20px;
    margin-bottom: 20px;
    padding: 15px;
    background: var(--input-bg);
    border-radius: 12px;
    border: 1px solid var(--border);
}

.radio-container {
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;
    padding: 12px 20px;
    border-radius: 10px;
    transition: all 0.2s;
    flex: 1;
    justify-content: center;
}

.radio-container:hover {
    background: var(--hover-bg);
}

.radio-container input[type="radio"] {
    display: none;
}

.radio-checkmark {
    width: 22px;
    height: 22px;
    border: 2px solid var(--border);
    border-radius: 50%;
    position: relative;
    transition: all 0.2s;
}

.radio-container input:checked + .radio-checkmark {
    border-color: var(--primary);
}

.radio-container input:checked + .radio-checkmark::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 10px;
    height: 10px;
    background: var(--primary);
    border-radius: 50%;
}

.sim-config-panel {
    padding: 20px;
    background: var(--card-bg);
    border-radius: 12px;
    border: 1px solid var(--border);
}

.config-hint {
    color: var(--text-secondary);
    font-size: 0.9rem;
    margin-bottom: 15px;
}

.sim-buttons {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 15px;
}

.sim-btn {
    padding: 25px 20px;
    border: 3px solid var(--border);
    border-radius: 16px;
    background: var(--input-bg);
    cursor: pointer;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    transition: all 0.3s;
    color: var(--text-primary);
}

.sim-btn:hover {
    border-color: var(--primary);
    background: var(--primary-light);
}

.sim-btn.active {
    border-color: var(--primary);
    background: var(--primary);
    color: white;
}

.sim-btn i {
    font-size: 2.5rem;
}

.sim-btn .sim-label {
    font-size: 1.2rem;
    font-weight: 700;
}

.sim-btn .sim-carrier {
    font-size: 0.85rem;
    opacity: 0.8;
}

.selected-sim-info {
    margin-top: 15px;
    padding: 12px;
    background: var(--success-light);
    border-radius: 8px;
    text-align: center;
    color: var(--success);
}

.ratio-inputs {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 20px;
    flex-wrap: wrap;
}

.ratio-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
}

.ratio-item label {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
}

.ratio-item input {
    width: 80px;
    padding: 15px;
    text-align: center;
    font-size: 1.5rem;
    font-weight: 700;
    border: 2px solid var(--border);
    border-radius: 12px;
    background: var(--input-bg);
    color: var(--text-primary);
}

.ratio-divider {
    font-size: 1.2rem;
    font-weight: 600;
    color: var(--text-secondary);
}

.ratio-visualization {
    margin-top: 20px;
    padding: 15px;
    background: var(--code-bg);
    border-radius: 10px;
    text-align: center;
}

.pattern-badges {
    display: inline-flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-left: 10px;
}

.pattern-badge {
    padding: 4px 12px;
    border-radius: 20px;
    font-size: 0.8rem;
    font-weight: 600;
}

.pattern-badge.sim1 {
    background: var(--primary);
    color: white;
}

.pattern-badge.sim2 {
    background: var(--success);
    color: white;
}

/* File Upload */
.file-upload-area {
    border: 2px dashed var(--border);
    border-radius: 12px;
    padding: 40px;
    text-align: center;
    cursor: pointer;
    transition: all 0.3s ease;
    background: var(--input-bg);
}

.file-upload-area:hover,
.file-upload-area.dragover {
    border-color: var(--primary);
    background: var(--primary-light);
}

.file-upload-area i {
    font-size: 3rem;
    color: var(--primary);
    margin-bottom: 15px;
}

.file-options {
    display: flex;
    flex-wrap: wrap;
    gap: 20px;
    margin-top: 20px;
    padding-top: 20px;
    border-top: 1px solid var(--border);
}

.checkbox-container {
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;
}

.checkbox-container input[type="checkbox"] {
    display: none;
}

.checkmark {
    width: 24px;
    height: 24px;
    border: 2px solid var(--border);
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.2s;
    background: var(--input-bg);
}

.checkbox-container:hover .checkmark {
    border-color: var(--primary);
}

.checkbox-container input:checked + .checkmark {
    background: var(--primary);
    border-color: var(--primary);
}

.checkbox-container input:checked + .checkmark::after {
    content: '✓';
    color: white;
    font-weight: bold;
}

.separator-config {
    display: flex;
    align-items: center;
    gap: 10px;
}

.separator-config select {
    padding: 10px 15px;
    border: 2px solid var(--border);
    border-radius: 8px;
    background: var(--input-bg);
    color: var(--text-primary);
}

/* Sheet Selector */
.sheet-selector {
    margin-top: 20px;
    padding: 15px;
    background: var(--code-bg);
    border-radius: 10px;
}

.sheet-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 10px;
}

.sheet-tab {
    padding: 10px 20px;
    border: 2px solid var(--border);
    border-radius: 8px;
    background: var(--card-bg);
    color: var(--text-primary);
    cursor: pointer;
    transition: all 0.2s;
}

.sheet-tab:hover {
    border-color: var(--primary);
}

.sheet-tab.active {
    background: var(--primary);
    color: white;
    border-color: var(--primary);
}

/* ========== FILE PREVIEW STYLES ========== */
.file-preview {
    margin-top: 20px;
    padding-top: 20px;
    border-top: 1px solid var(--border);
}

.file-preview h3 {
    font-size: 1rem;
    margin-bottom: 15px;
    color: var(--text-primary);
    display: flex;
    align-items: center;
    gap: 8px;
}

.preview-info {
    display: flex;
    gap: 12px;
    margin-bottom: 15px;
    flex-wrap: wrap;
    align-items: center;
}

.preview-info span {
    padding: 6px 14px;
    background: var(--primary-light);
    color: var(--primary);
    border-radius: 20px;
    font-size: 0.85rem;
    font-weight: 500;
    display: flex;
    align-items: center;
    gap: 6px;
}

.preview-info .header-badge {
    background: var(--warning-light);
    color: var(--warning);
}

.preview-table-wrapper {
    position: relative;
    border: 2px solid var(--border);
    border-radius: 12px;
    overflow: hidden;
}

.preview-table-container {
    max-height: 400px;
    overflow: auto;
    background: var(--input-bg);
}

.preview-table-container::-webkit-scrollbar {
    width: 14px;
    height: 14px;
}

.preview-table-container::-webkit-scrollbar-track {
    background: var(--code-bg);
}

.preview-table-container::-webkit-scrollbar-thumb {
    background: var(--primary);
    border-radius: 7px;
    border: 3px solid var(--code-bg);
}

.preview-table-container::-webkit-scrollbar-thumb:hover {
    background: var(--primary-dark);
}

.preview-table-container::-webkit-scrollbar-corner {
    background: var(--code-bg);
}

.preview-table-container table {
    width: max-content;
    min-width: 100%;
    border-collapse: collapse;
    font-size: 0.85rem;
}

.preview-table-container th,
.preview-table-container td {
    padding: 12px 16px;
    border: 1px solid var(--border);
    text-align: left;
    white-space: nowrap;
    min-width: 120px;
    max-width: 300px;
    overflow: hidden;
    text-overflow: ellipsis;
}

.preview-table-container thead {
    position: sticky;
    top: 0;
    z-index: 20;
}

.preview-table-container th {
    background: var(--primary);
    color: white;
    font-weight: 600;
}

.preview-table-container th .col-var {
    display: block;
    font-size: 0.7rem;
    font-weight: 400;
    opacity: 0.85;
    font-family: monospace;
    margin-top: 4px;
}

.preview-table-container th.row-index-header,
.preview-table-container td.row-index {
    position: sticky;
    left: 0;
    z-index: 15;
    min-width: 60px;
    max-width: 60px;
    width: 60px;
    text-align: center;
    font-weight: 600;
}

.preview-table-container th.row-index-header {
    z-index: 25;
    background: var(--primary-dark);
}

.preview-table-container td.row-index {
    background: var(--code-bg);
    color: var(--text-secondary);
    border-right: 2px solid var(--primary);
}

.preview-table-container tbody tr:nth-child(even) {
    background: rgba(0,0,0,0.02);
}

.preview-table-container tbody tr:nth-child(even) td.row-index {
    background: var(--hover-bg);
}

.preview-table-container tbody tr:hover td {
    background: var(--primary-light);
}

.preview-table-container tbody tr:hover td.row-index {
    background: var(--primary);
    color: white;
}

.preview-table-container tr.header-row {
    background: var(--warning-light) !important;
}

.preview-table-container tr.header-row td {
    font-weight: 600;
    color: var(--warning);
}

.preview-table-container tr.header-row td.row-index {
    background: var(--warning);
    color: white;
}

.preview-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    background: var(--code-bg);
    border-top: 1px solid var(--border);
    font-size: 0.85rem;
    color: var(--text-secondary);
}

.preview-footer .scroll-hint {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--primary);
    font-weight: 500;
}

.preview-footer .scroll-hint i {
    animation: scrollHint 1.5s ease-in-out infinite;
}

@keyframes scrollHint {
    0%, 100% { transform: translateX(0); }
    50% { transform: translateX(5px); }
}

.available-variables {
    margin-top: 20px;
    padding: 15px;
    background: var(--code-bg);
    border-radius: 10px;
    border: 1px solid var(--border);
}

.available-variables h4 {
    font-size: 0.9rem;
    margin-bottom: 12px;
    color: var(--text-secondary);
    display: flex;
    align-items: center;
    gap: 8px;
}

.variable-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.variable-tags .tag {
    padding: 8px 14px;
    background: var(--success-light);
    color: var(--success);
    border-radius: 20px;
    font-size: 0.85rem;
    cursor: pointer;
    transition: all 0.2s;
    font-family: monospace;
    display: flex;
    align-items: center;
    gap: 6px;
}

.variable-tags .tag:hover {
    background: var(--success);
    color: white;
    transform: translateY(-2px);
}

.variable-tags .tag .tag-name {
    font-family: 'Inter', sans-serif;
    font-size: 0.75rem;
    opacity: 0.8;
}

/* Row Range Section */
.row-range-section input {
    font-family: monospace;
}

.range-hints {
    display: flex;
    flex-wrap: wrap;
    gap: 15px;
    margin-top: 10px;
}

.hint-item {
    font-size: 0.85rem;
    color: var(--text-secondary);
}

.hint-item code {
    background: var(--code-bg);
    padding: 2px 8px;
    border-radius: 4px;
    font-family: monospace;
}

.range-info {
    margin-top: 10px;
    padding: 10px 15px;
    background: var(--code-bg);
    border-radius: 8px;
    font-size: 0.9rem;
    color: var(--text-secondary);
}

.range-info.has-range {
    background: var(--success-light);
    color: var(--success);
}

/* Form Groups */
.form-group {
    margin-bottom: 20px;
}

.form-group label {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
    font-weight: 500;
    color: var(--text-primary);
}

.form-group label .hint {
    font-weight: 400;
    font-size: 0.8rem;
    color: var(--text-secondary);
    margin-left: auto;
}

.form-group textarea,
.form-group input[type="text"] {
    width: 100%;
    padding: 14px 18px;
    border: 2px solid var(--border);
    border-radius: 10px;
    font-size: 1rem;
    font-family: inherit;
    resize: vertical;
    min-height: 80px;
    background: var(--input-bg);
    color: var(--text-primary);
    transition: border-color 0.2s;
}

.form-group input[type="text"] {
    min-height: auto;
}

.form-group textarea:focus,
.form-group input:focus {
    outline: none;
    border-color: var(--primary);
}

.message-info {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 10px;
    padding: 10px 15px;
    background: var(--code-bg);
    border-radius: 8px;
}

.char-counter {
    display: flex;
    align-items: center;
    gap: 10px;
    color: var(--text-secondary);
}

.encoding-badge {
    padding: 3px 10px;
    background: var(--success-light);
    color: var(--success);
    border-radius: 12px;
    font-size: 0.8rem;
    font-weight: 600;
}

.encoding-badge.ucs2 {
    background: var(--warning-light);
    color: var(--warning);
}

/* Variables Panel */
.variables-panel {
    background: var(--code-bg);
    border-radius: 10px;
    padding: 15px;
    margin-bottom: 20px;
}

.variables-panel h4 {
    font-size: 0.9rem;
    margin-bottom: 12px;
    color: var(--text-secondary);
}

.variable-buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.var-btn {
    padding: 8px 14px;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--card-bg);
    color: var(--text-primary);
    font-size: 0.85rem;
    cursor: pointer;
    transition: all 0.2s;
}

.var-btn:hover {
    background: var(--primary);
    color: white;
    border-color: var(--primary);
}

.dynamic-variables {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--border);
}

.dynamic-variables:empty {
    display: none;
}

/* Interval Slider */
.interval-group {
    padding: 20px;
    background: var(--code-bg);
    border-radius: 12px;
}

.interval-value {
    font-weight: 600;
    color: var(--primary);
    margin-left: auto;
}

.slider {
    width: 100%;
    height: 8px;
    border-radius: 4px;
    background: var(--border);
    outline: none;
    -webkit-appearance: none;
    margin: 20px 0 10px;
}

.slider::-webkit-slider-thumb {
    -webkit-appearance: none;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: var(--primary);
    cursor: pointer;
}

.slider-labels {
    display: flex;
    justify-content: space-between;
    font-size: 0.85rem;
    color: var(--text-secondary);
}

/* Buttons */
.control-buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 20px;
}

.btn {
    padding: 14px 28px;
    border: none;
    border-radius: 10px;
    font-size: 1rem;
    font-weight: 600;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 8px;
    transition: all 0.3s;
}

.btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.btn-primary { background: var(--primary); color: white; }
.btn-primary:hover:not(:disabled) { background: var(--primary-dark); }
.btn-success { background: var(--success); color: white; }
.btn-warning { background: var(--warning); color: white; }
.btn-danger { background: var(--error); color: white; }
.btn-secondary { background: var(--text-secondary); color: white; }
.btn-outline { background: transparent; color: var(--primary); border: 2px solid var(--primary); }
.btn-outline:hover:not(:disabled) { background: var(--primary); color: white; }

/* Progress */
.progress-stats {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 15px;
    margin-bottom: 25px;
}

.stat-box {
    text-align: center;
    padding: 20px;
    background: var(--code-bg);
    border-radius: 12px;
}

.stat-box .stat-value {
    display: block;
    font-size: 2rem;
    font-weight: 700;
    color: var(--text-primary);
}

.stat-box .stat-label {
    font-size: 0.85rem;
    color: var(--text-secondary);
}

.stat-box.success .stat-value { color: var(--success); }
.stat-box.error .stat-value { color: var(--error); }
.stat-box.pending .stat-value { color: var(--warning); }

.progress-bar-container {
    height: 30px;
    background: var(--border);
    border-radius: 15px;
    overflow: hidden;
}

.progress-bar {
    height: 100%;
    background: linear-gradient(90deg, var(--primary), var(--success));
    width: 0%;
    transition: width 0.3s;
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 60px;
    border-radius: 15px;
}

.progress-text {
    color: white;
    font-weight: 700;
}

.sim-usage-stats {
    display: flex;
    justify-content: center;
    gap: 40px;
    margin-top: 20px;
    padding: 15px;
    background: var(--code-bg);
    border-radius: 10px;
}

.sim-usage-item {
    display: flex;
    align-items: center;
    gap: 8px;
}

.sim-usage-item i {
    color: var(--primary);
}

.sim-usage-item strong {
    color: var(--primary);
    font-size: 1.1rem;
}

/* Logs */
.logs-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 15px;
}

.logs-header h2 {
    margin-bottom: 0;
}

.logs-container {
    max-height: 400px;
    overflow-y: auto;
    border: 1px solid var(--border);
    border-radius: 10px;
    background: var(--input-bg);
}

.log-placeholder {
    text-align: center;
    padding: 60px 20px;
    color: var(--text-secondary);
}

.log-placeholder i {
    font-size: 3rem;
    margin-bottom: 15px;
    opacity: 0.3;
}

.log-entry {
    display: grid;
    grid-template-columns: 80px 130px 70px 70px 1fr;
    gap: 10px;
    align-items: center;
    padding: 12px 16px;
    border-bottom: 1px solid var(--border);
    font-size: 0.9rem;
}

.log-entry:last-child {
    border-bottom: none;
}

.log-entry .log-time {
    color: var(--text-secondary);
    font-size: 0.8rem;
}

.log-entry .log-number {
    font-weight: 500;
    font-family: monospace;
}

.log-entry .log-sim {
    padding: 3px 10px;
    border-radius: 12px;
    font-size: 0.75rem;
    font-weight: 600;
    text-align: center;
    background: var(--primary-light);
    color: var(--primary);
}

.log-entry .log-sim.sim2 {
    background: var(--success-light);
    color: var(--success);
}

.log-entry .log-status {
    padding: 3px 10px;
    border-radius: 12px;
    font-size: 0.75rem;
    font-weight: 600;
    text-align: center;
}

.log-entry .log-status.sent {
    background: var(--success-light);
    color: var(--success);
}

.log-entry .log-status.failed {
    background: var(--error-light);
    color: var(--error);
}

.log-entry .log-message {
    color: var(--text-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

/* Footer */
.footer {
    text-align: center;
    padding: 20px;
    color: var(--text-secondary);
}

/* Utilities */
.hidden {
    display: none !important;
}

/* Responsive */
@media (max-width: 768px) {
    .progress-stats {
        grid-template-columns: repeat(2, 1fr);
    }
    
    .sim-buttons {
        grid-template-columns: 1fr;
    }
    
    .log-entry {
        grid-template-columns: 1fr;
        gap: 5px;
    }
    
    .sim-selection-mode {
        flex-direction: column;
    }
    
    .preview-table-container {
        max-height: 300px;
    }
}
""".trimIndent()

    fun getJs(): String = """
(function(){
    var _f = window.fetch;
    window.fetch = function(){
        return _f.apply(this, arguments).then(function(r){
            if (r.status === 401) { window.location.href = '/'; }
            return r;
        });
    };
})();
class TurboSMS {
    constructor() {
        this.uploadedData = null;
        this.isPaused = false;
        this.isRunning = false;
        this.statusInterval = null;
        this.currentSheets = [];
        this.currentSheetIndex = 0;
        this.hasHeader = false;
        this.headerRow = [];
        this.fileUploaded = false;
        
        this.simMode = 'single';
        this.selectedSim = 1;
        this.simRatios = { 1: 1, 2: 1 };
        
        this.init();
    }
    
    init() {
        this.initTheme();
        this.bindEvents();
        this.startStatusPolling();
        this.loadSims();
        this.updateRatioVisualization();
    }
    
    initTheme() {
        const savedTheme = localStorage.getItem('turbo-sms-theme');
        if (savedTheme === 'dark') {
            document.documentElement.setAttribute('data-theme', 'dark');
            this.updateThemeIcon(true);
        }
    }
    
    updateThemeIcon(isDark) {
        const icon = document.getElementById('themeIcon');
        icon.className = isDark ? 'fas fa-sun' : 'fas fa-moon';
    }
    
    toggleTheme() {
        const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        if (isDark) {
            document.documentElement.removeAttribute('data-theme');
            localStorage.setItem('turbo-sms-theme', 'light');
        } else {
            document.documentElement.setAttribute('data-theme', 'dark');
            localStorage.setItem('turbo-sms-theme', 'dark');
        }
        this.updateThemeIcon(!isDark);
    }
    
    async loadSims() {
        const banner = document.getElementById('simInfoBanner');
        try {
            const response = await fetch('/api/sims');
            const result = await response.json();
            
            if (result.success && result.sims) {
                if (result.sims.length >= 1) {
                    const sim1 = result.sims.find(s => s.slot === 1) || result.sims[0];
                    document.getElementById('sim1Carrier').textContent = sim1.carrier || 'SIM 1';
                }
                if (result.sims.length >= 2) {
                    const sim2 = result.sims.find(s => s.slot === 2) || result.sims[1];
                    document.getElementById('sim2Carrier').textContent = sim2.carrier || 'SIM 2';
                }
                
                banner.innerHTML = '<i class="fas fa-check-circle"></i> <span>' + result.sims.length + ' SIM(s) detected</span>';
                banner.className = 'sim-info-banner success';
            }
        } catch (error) {
            console.error('Error loading SIMs:', error);
        }
    }
    
    bindEvents() {
        document.getElementById('themeToggle').addEventListener('click', () => this.toggleTheme());
        
        document.getElementById('simModeSingle').addEventListener('change', () => this.handleSimModeChange('single'));
        document.getElementById('simModeRatio').addEventListener('change', () => this.handleSimModeChange('ratio'));
        
        document.getElementById('simBtn1').addEventListener('click', () => this.selectSim(1));
        document.getElementById('simBtn2').addEventListener('click', () => this.selectSim(2));
        
        document.getElementById('sim1Ratio').addEventListener('input', () => this.updateRatioVisualization());
        document.getElementById('sim2Ratio').addEventListener('input', () => this.updateRatioVisualization());
        
        const dropZone = document.getElementById('dropZone');
        const fileInput = document.getElementById('fileInput');
        
        dropZone.addEventListener('click', () => fileInput.click());
        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.classList.add('dragover');
        });
        dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragover'));
        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('dragover');
            if (e.dataTransfer.files[0]) this.handleFileUpload(e.dataTransfer.files[0]);
        });
        fileInput.addEventListener('change', (e) => {
            if (e.target.files[0]) this.handleFileUpload(e.target.files[0]);
        });
        
        document.getElementById('hasHeader').addEventListener('change', (e) => {
            this.hasHeader = e.target.checked;
            if (this.fileUploaded) this.updateHeaderSetting();
        });
        
        document.getElementById('message').addEventListener('input', () => this.updateCharacterCount());
        
        document.getElementById('intervalSlider').addEventListener('input', (e) => {
            const val = e.target.value;
            document.getElementById('intervalValue').textContent = val + ' second' + (val > 1 ? 's' : '');
        });
        
        document.querySelectorAll('.var-btn').forEach(btn => {
            btn.addEventListener('click', () => this.insertVariable(btn.dataset.var));
        });
        
        document.getElementById('rowRange').addEventListener('input', (e) => this.updateRangeInfo(e.target.value));
        
        document.getElementById('btnPrepare').addEventListener('click', () => this.prepareSms());
        document.getElementById('btnStart').addEventListener('click', () => this.controlSms('start'));
        document.getElementById('btnPause').addEventListener('click', () => {
            this.controlSms(this.isPaused ? 'resume' : 'pause');
        });
        document.getElementById('btnStop').addEventListener('click', () => this.controlSms('stop'));
        document.getElementById('btnReset').addEventListener('click', () => this.controlSms('reset'));
        document.getElementById('btnExport').addEventListener('click', () => this.exportLogs());
    }
    
    handleSimModeChange(mode) {
        this.simMode = mode;
        if (mode === 'single') {
            document.getElementById('singleSimConfig').classList.remove('hidden');
            document.getElementById('ratioSimConfig').classList.add('hidden');
        } else {
            document.getElementById('singleSimConfig').classList.add('hidden');
            document.getElementById('ratioSimConfig').classList.remove('hidden');
        }
    }
    
    selectSim(simNumber) {
        this.selectedSim = simNumber;
        document.getElementById('simBtn1').classList.toggle('active', simNumber === 1);
        document.getElementById('simBtn2').classList.toggle('active', simNumber === 2);
        document.getElementById('currentSimDisplay').textContent = 'SIM ' + simNumber;
    }
    
    updateRatioVisualization() {
        const sim1 = parseInt(document.getElementById('sim1Ratio').value) || 0;
        const sim2 = parseInt(document.getElementById('sim2Ratio').value) || 0;
        this.simRatios = { 1: sim1, 2: sim2 };
        
        const pattern = document.getElementById('ratioPattern');
        let badges = '';
        
        for (let i = 0; i < Math.min(sim1, 5); i++) {
            badges += '<span class="pattern-badge sim1">SIM1</span>';
        }
        for (let i = 0; i < Math.min(sim2, 5); i++) {
            badges += '<span class="pattern-badge sim2">SIM2</span>';
        }
        
        pattern.innerHTML = badges || '<span style="color:var(--text-secondary)">Set ratio above 0</span>';
    }
    
    getSimConfig() {
        if (this.simMode === 'single') {
            return { selectedSim: this.selectedSim, simRatios: { 1: 0, 2: 0 } };
        } else {
            return {
                selectedSim: 0,
                simRatios: {
                    1: parseInt(document.getElementById('sim1Ratio').value) || 0,
                    2: parseInt(document.getElementById('sim2Ratio').value) || 0
                }
            };
        }
    }
    
    updateRangeInfo(rangeStr) {
        const info = document.getElementById('rangeInfo');
        if (!rangeStr.trim()) {
            info.textContent = 'All rows will be processed';
            info.className = 'range-info';
            return;
        }
        
        try {
            const indices = this.parseRange(rangeStr);
            if (indices.length > 0) {
                info.textContent = '✓ ' + indices.length + ' row(s) selected';
                info.className = 'range-info has-range';
            } else {
                info.textContent = 'No valid rows';
                info.className = 'range-info';
            }
        } catch (e) {
            info.textContent = 'Invalid format';
            info.className = 'range-info';
        }
    }
    
    parseRange(rangeStr) {
        const indices = new Set();
        const parts = rangeStr.split(',').map(p => p.trim()).filter(p => p);
        
        for (const part of parts) {
            if (part.includes('-')) {
                const [start, end] = part.split('-').map(n => parseInt(n.trim()));
                if (!isNaN(start) && !isNaN(end)) {
                    for (let i = Math.min(start, end); i <= Math.max(start, end); i++) {
                        indices.add(i);
                    }
                }
            } else {
                const num = parseInt(part);
                if (!isNaN(num)) indices.add(num);
            }
        }
        
        return Array.from(indices).sort((a, b) => a - b);
    }
    
    async handleFileUpload(file) {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('filename', file.name);
        formData.append('hasHeader', this.hasHeader.toString());
        formData.append('separator', document.getElementById('separator').value);
        
        try {
            const response = await fetch('/api/upload-file', { method: 'POST', body: formData });
            const result = await response.json();
            
            if (result.success) {
                this.fileUploaded = true;
                this.uploadedData = result;
                this.hasHeader = result.hasHeader;
                this.headerRow = result.headerRow || [];
                
                document.getElementById('hasHeader').checked = this.hasHeader;
                this.showFilePreview(result);
                this.updateColumnVariables(result.columns, result.headerRow);
                
                if (result.isExcel && result.sheets && result.sheets.length > 0) {
                    this.currentSheets = result.sheets;
                    this.showSheetSelector(result.sheets, result.currentSheet);
                } else {
                    document.getElementById('sheetSelector').classList.add('hidden');
                }
            } else {
                alert('Error: ' + result.error);
            }
        } catch (error) {
            console.error('Upload error:', error);
            alert('Error uploading file');
        }
    }
    
    async updateHeaderSetting() {
        try {
            const response = await fetch('/api/set-header', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ hasHeader: this.hasHeader })
            });
            const result = await response.json();
            if (result.success && result.preview) {
                this.uploadedData = result;
                this.headerRow = result.headerRow || [];
                this.showFilePreview(result);
                this.updateColumnVariables(result.columns, result.headerRow);
            }
        } catch (error) {
            console.error('Error:', error);
        }
    }
    
    showSheetSelector(sheets, currentIndex) {
        const selector = document.getElementById('sheetSelector');
        const tabs = document.getElementById('sheetTabs');
        selector.classList.remove('hidden');
        tabs.innerHTML = '';
        
        sheets.forEach((name, i) => {
            const tab = document.createElement('button');
            tab.className = 'sheet-tab' + (i === currentIndex ? ' active' : '');
            tab.textContent = name;
            tab.addEventListener('click', () => this.selectSheet(i));
            tabs.appendChild(tab);
        });
    }
    
    async selectSheet(index) {
        try {
            const response = await fetch('/api/select-sheet', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sheetIndex: index })
            });
            const result = await response.json();
            if (result.success) {
                this.currentSheetIndex = index;
                this.uploadedData = result;
                this.headerRow = result.headerRow || [];
                this.showFilePreview(result);
                this.updateColumnVariables(result.columns, result.headerRow);
                
                document.querySelectorAll('.sheet-tab').forEach((tab, i) => {
                    tab.classList.toggle('active', i === index);
                });
            }
        } catch (error) {
            console.error('Error:', error);
        }
    }
    
    // UPDATED: Show file preview with ALL rows and scrollable table
    showFilePreview(data) {
        const preview = document.getElementById('filePreview');
        preview.classList.remove('hidden');
        
        const dataRowCount = data.rows || 0;
        const totalRows = data.hasHeader ? dataRowCount + 1 : dataRowCount;
        
        document.getElementById('rowCount').innerHTML = '<i class="fas fa-list"></i> ' + dataRowCount + ' data rows';
        document.getElementById('colCount').innerHTML = '<i class="fas fa-columns"></i> ' + data.columns + ' columns';
        
        const headerBadge = document.getElementById('headerBadge');
        headerBadge.classList.toggle('hidden', !data.hasHeader);
        
        const table = document.getElementById('previewTable');
        table.innerHTML = '';
        
        const allPreviewData = data.preview || [];
        const columns = data.columns || (allPreviewData[0] ? allPreviewData[0].length : 0);
        
        // Create thead
        const thead = document.createElement('thead');
        const headerRow = document.createElement('tr');
        
        // Row index header
        const indexTh = document.createElement('th');
        indexTh.className = 'row-index-header';
        indexTh.textContent = '#';
        headerRow.appendChild(indexTh);
        
        // Column headers
        for (let i = 0; i < columns; i++) {
            const th = document.createElement('th');
            if (data.hasHeader && data.headerRow && data.headerRow[i]) {
                th.innerHTML = this.escapeHtml(data.headerRow[i]) + 
                    '<span class="col-var">{col-' + (i + 1) + '}</span>';
            } else {
                th.innerHTML = 'Column ' + (i + 1) + 
                    '<span class="col-var">{col-' + (i + 1) + '}</span>';
            }
            headerRow.appendChild(th);
        }
        thead.appendChild(headerRow);
        table.appendChild(thead);
        
        // Create tbody
        const tbody = document.createElement('tbody');
        let displayIndex = 1;
        
        allPreviewData.forEach((row, rowIndex) => {
            const tr = document.createElement('tr');
            const isHeaderRow = data.hasHeader && rowIndex === 0;
            
            if (isHeaderRow) {
                tr.className = 'header-row';
            }
            
            // Row index cell
            const indexTd = document.createElement('td');
            indexTd.className = 'row-index';
            if (isHeaderRow) {
                indexTd.innerHTML = '<i class="fas fa-heading" title="Header"></i>';
            } else {
                indexTd.textContent = displayIndex;
                displayIndex++;
            }
            tr.appendChild(indexTd);
            
            // Data cells
            for (let i = 0; i < columns; i++) {
                const td = document.createElement('td');
                const cellValue = row && row[i] !== undefined && row[i] !== null ? String(row[i]) : '';
                td.textContent = cellValue;
                td.title = cellValue;
                tr.appendChild(td);
            }
            
            tbody.appendChild(tr);
        });
        
        table.appendChild(tbody);
        
        // Update stats
        const previewStats = document.getElementById('previewStats');
        if (data.hasHeader) {
            previewStats.textContent = 'Showing ' + dataRowCount + ' data rows (+ 1 header)';
        } else {
            previewStats.textContent = 'Showing all ' + totalRows + ' rows';
        }
        
        // Check scroll hint visibility
        const container = document.getElementById('previewTableContainer');
        setTimeout(() => {
            const hasScroll = container.scrollWidth > container.clientWidth || container.scrollHeight > container.clientHeight;
            const hint = document.querySelector('.scroll-hint');
            if (hint) hint.style.display = hasScroll ? 'flex' : 'none';
        }, 100);
    }
    
    updateColumnVariables(columns, headerRow) {
        const container = document.getElementById('columnVariables');
        const dynamicVars = document.getElementById('dynamicVariables');
        container.innerHTML = '';
        dynamicVars.innerHTML = '';
        
        for (let i = 1; i <= columns; i++) {
            const varName = '{col-' + i + '}';
            const displayName = (headerRow && headerRow[i-1]) ? headerRow[i-1] : 'Column ' + i;
            
            const tag = document.createElement('span');
            tag.className = 'tag';
            tag.innerHTML = '<span class="tag-var">' + varName + '</span>';
            if (headerRow && headerRow[i-1]) {
                tag.innerHTML += '<span class="tag-name">(' + this.escapeHtml(headerRow[i-1]) + ')</span>';
            }
            tag.title = 'Click to insert ' + varName;
            tag.addEventListener('click', () => {
                this.insertVariable(varName);
                this.showCopyFeedback(tag);
            });
            container.appendChild(tag);
            
            const btn = document.createElement('button');
            btn.className = 'var-btn';
            btn.innerHTML = '<i class="fas fa-columns"></i> ' + this.escapeHtml(displayName);
            btn.title = varName;
            btn.addEventListener('click', () => this.insertVariable(varName));
            dynamicVars.appendChild(btn);
        }
    }
    
    showCopyFeedback(element) {
        const original = element.style.background;
        element.style.background = 'var(--success)';
        element.style.color = 'white';
        setTimeout(() => {
            element.style.background = '';
            element.style.color = '';
        }, 300);
    }
    
    insertVariable(variable) {
        const input = document.getElementById('message');
        const start = input.selectionStart;
        const end = input.selectionEnd;
        input.value = input.value.substring(0, start) + variable + input.value.substring(end);
        input.focus();
        input.setSelectionRange(start + variable.length, start + variable.length);
        this.updateCharacterCount();
    }
    
    updateCharacterCount() {
        const message = document.getElementById('message').value;
        const isUnicode = /[^\x00-\x7F]/.test(message);
        
        const badge = document.getElementById('encodingBadge');
        let maxPart, maxMulti;
        
        if (isUnicode) {
            maxPart = 70;
            maxMulti = 67;
            badge.textContent = 'Unicode (UCS-2)';
            badge.className = 'encoding-badge ucs2';
        } else {
            maxPart = 160;
            maxMulti = 153;
            badge.textContent = 'GSM-7';
            badge.className = 'encoding-badge';
        }
        
        const len = message.length;
        const parts = len <= maxPart ? 1 : Math.ceil(len / maxMulti);
        
        document.getElementById('charCount').textContent = len;
        document.getElementById('maxChars').textContent = parts > 1 ? maxMulti : maxPart;
        document.getElementById('totalParts').textContent = parts;
    }
    
    async prepareSms() {
        const numbers = document.getElementById('numbers').value.trim();
        const message = document.getElementById('message').value.trim();
        const interval = document.getElementById('intervalSlider').value;
        const rowRange = document.getElementById('rowRange').value.trim();
        
        if (!numbers) {
            alert('Please enter phone numbers');
            return;
        }
        if (!message) {
            alert('Please enter a message');
            return;
        }
        
        const simConfig = this.getSimConfig();
        
        try {
            const response = await fetch('/api/send-sms', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                body: JSON.stringify({
                    numbers, message,
                    interval: parseInt(interval),
                    rowRange,
                    selectedSim: simConfig.selectedSim,
                    simRatios: simConfig.simRatios
                })
            });
            
            const result = await response.json();
            
            if (result.success) {
                document.getElementById('statTotal').textContent = result.totalTasks;
                document.getElementById('statPending').textContent = result.totalTasks;
                document.getElementById('btnStart').disabled = false;
                alert('✓ Prepared ' + result.totalTasks + ' messages. Click Start to begin.');
            } else {
                alert('Error: ' + result.error);
            }
        } catch (error) {
            console.error('Error:', error);
            alert('Error preparing SMS');
        }
    }
    
    async controlSms(action) {
        try {
            const response = await fetch('/api/control', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action })
            });
            const result = await response.json();
            if (result.success) {
                this.updateButtons(action);
                this.updateStatus(result.status);
            }
        } catch (error) {
            console.error('Error:', error);
        }
    }
    
    updateButtons(action) {
        const btnStart = document.getElementById('btnStart');
        const btnPause = document.getElementById('btnPause');
        const btnStop = document.getElementById('btnStop');
        
        switch (action) {
            case 'start':
            case 'resume':
                this.isRunning = true;
                this.isPaused = false;
                btnStart.disabled = true;
                btnPause.disabled = false;
                btnPause.innerHTML = '<i class="fas fa-pause"></i> Pause';
                btnStop.disabled = false;
                break;
            case 'pause':
                this.isPaused = true;
                btnPause.innerHTML = '<i class="fas fa-play"></i> Resume';
                break;
            case 'stop':
            case 'reset':
                this.isRunning = false;
                this.isPaused = false;
                btnStart.disabled = false;
                btnPause.disabled = true;
                btnPause.innerHTML = '<i class="fas fa-pause"></i> Pause';
                btnStop.disabled = true;
                break;
        }
    }
    
    updateStatus(status) {
        document.getElementById('statTotal').textContent = status.total || 0;
        document.getElementById('statSent').textContent = status.sent || 0;
        document.getElementById('statFailed').textContent = status.failed || 0;
        document.getElementById('statPending').textContent = (status.total || 0) - (status.sent || 0) - (status.failed || 0);
        
        const progress = status.progress || 0;
        document.getElementById('progressBar').style.width = Math.max(progress, 3) + '%';
        document.getElementById('progressText').textContent = progress + '%';
    }
    
    startStatusPolling() {
        this.statusInterval = setInterval(async () => {
            try {
                const [statusRes, logsRes] = await Promise.all([
                    fetch('/api/status'),
                    fetch('/api/logs')
                ]);
                
                const status = await statusRes.json();
                const logs = await logsRes.json();
                
                this.updateStatus(status);
                this.updateLogs(logs.logs);
            } catch (error) {}
        }, 1000);
    }
    
    updateLogs(logs) {
        if (!logs || logs.length === 0) return;
        
        const container = document.getElementById('logsContainer');
        container.innerHTML = '';
        
        let sim1Count = 0, sim2Count = 0;
        
        logs.slice().reverse().forEach(log => {
            if (log.status === 'SENT') {
                if (log.sim === 1) sim1Count++;
                else if (log.sim === 2) sim2Count++;
            }
            
            const entry = document.createElement('div');
            entry.className = 'log-entry';
            
            const time = (log.timestamp || '').split(' ')[1] || log.timestamp;
            const simClass = log.sim === 2 ? 'sim2' : '';
            
            entry.innerHTML = 
                '<span class="log-time">' + this.escapeHtml(time) + '</span>' +
                '<span class="log-number">' + this.escapeHtml(log.number) + '</span>' +
                '<span class="log-sim ' + simClass + '">SIM ' + log.sim + '</span>' +
                '<span class="log-status ' + log.status.toLowerCase() + '">' + log.status + '</span>' +
                '<span class="log-message">' + this.escapeHtml(log.message || '') + '</span>';
            
            container.appendChild(entry);
        });
        
        document.getElementById('sim1SentCount').textContent = sim1Count;
        document.getElementById('sim2SentCount').textContent = sim2Count;
    }
    
    exportLogs() {
        window.location.href = '/api/export-logs';
    }
    
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.turboSMS = new TurboSMS();
});
""".trimIndent()
}