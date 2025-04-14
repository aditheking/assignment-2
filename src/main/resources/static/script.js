document.addEventListener('DOMContentLoaded', () => {
    console.log('DOM fully loaded and parsed');

    const configForm = document.getElementById('config-form');
    const statusAlert = document.getElementById('status-alert');
    const resultsAlert = document.getElementById('results-alert');
    const statusMessageSpan = document.getElementById('status-message');
    const resultsMessageSpan = document.getElementById('results-message');
    const connectChBtn = document.getElementById('connect-ch-btn');
    const loadColumnsBtn = document.getElementById('load-columns-btn');
    const previewBtn = document.getElementById('preview-btn');

    const chConfigFieldset = document.getElementById('ch-config');
    const ffConfigFieldset = document.getElementById('ff-config');
    const chTablesSection = document.getElementById('ch-tables-section');
    const chTableSelect = document.getElementById('ch-table-select');
    const columnSelectionSection = document.getElementById('column-selection');
    const columnsListDiv = document.getElementById('columns-list');
    const previewDataSection = document.getElementById('preview-data');
    const previewTableHead = document.querySelector('#preview-table thead');
    const previewTableBody = document.querySelector('#preview-table tbody');
    const selectAllColsBtn = document.getElementById('select-all-cols-btn');
    const deselectAllColsBtn = document.getElementById('deselect-all-cols-btn');

    const API_BASE_URL = '/api';

    function updateStatus(message, isError = false, isLoading = false) {
        if (!statusAlert) return;
        const icon = statusAlert.querySelector('i');
        const textSpan = statusAlert.querySelector('span');
        if (!icon || !textSpan) return;

        textSpan.textContent = message;
        let alertClass = 'alert-info';
        let iconClass = 'bi-info-circle-fill';
        if (!isLoading && isError) {
            alertClass = 'alert-danger';
            iconClass = 'bi-exclamation-triangle-fill';
        } else if (!isLoading && !isError) {
            alertClass = 'alert-primary';
            iconClass = 'bi-info-circle-fill';
        }
        statusAlert.className = `alert ${alertClass} d-flex align-items-center`;
        icon.className = `${iconClass} me-2`;
        console.log(`Status: ${message}`);
    }

    function updateResults(message, isSuccess = false) {
        if (!resultsAlert) return;
        const icon = resultsAlert.querySelector('i');
        const textSpan = resultsAlert.querySelector('span');
        if (!icon || !textSpan) return;

        textSpan.textContent = message;
        let alertClass = 'alert-light';
        let iconClass = 'bi-clipboard-check';
        if (isSuccess) {
            alertClass = 'alert-success';
            iconClass = 'bi-check-circle-fill';
        } else if (message !== 'N/A') {
             alertClass = 'alert-warning';
             iconClass = 'bi-exclamation-octagon-fill';
        }
        resultsAlert.className = `alert ${alertClass} d-flex align-items-center`;
        icon.className = `${iconClass} me-2`;
        console.log(`Result: ${message}`);
    }

    function toggleElement(element, show) {
        if (element) {
            element.style.display = show ? '' : 'none';
        }
    }

    function clearTableSelect() {
         if (!chTableSelect) return;
         chTableSelect.innerHTML = '<option value="">-- Select Table --</option>';
         toggleElement(chTablesSection, false);
    }

     function clearColumnList() {
        if (!columnsListDiv) return;
        columnsListDiv.innerHTML = '';
        toggleElement(columnSelectionSection, false);
        toggleElement(previewBtn, false);
        toggleElement(previewDataSection, false);
        updateResults('N/A');
    }

    function clearPreview() {
        if (previewTableHead) previewTableHead.innerHTML = '';
        if (previewTableBody) previewTableBody.innerHTML = '';
        toggleElement(previewDataSection, false);
    }

    function getClickHouseConnectionDetails(formData) {
        const portVal = formData.get('ch_port');
        return {
            host: formData.get('ch_host'),
            port: portVal ? parseInt(portVal, 10) : null,
            database: formData.get('ch_database'),
            user: formData.get('ch_user'),
            token: formData.get('ch_token')
        };
    }

    function toggleButtonLoading(button, isLoading) {
        if (!button) return;
        const spinner = button.querySelector('.spinner-border');
        const icon = button.querySelector('i');

        if (isLoading) {
            button.disabled = true;
            if (spinner) spinner.style.display = '';
            if (icon) icon.style.display = 'none';
        } else {
            button.disabled = false;
            if (spinner) spinner.style.display = 'none';
            if (icon) icon.style.display = '';
        }
    }

    async function makeApiCall(endpoint, method = 'POST', body = null, isFormData = false, buttonElement = null) {
        const options = {
            method: method,
            headers: { 'Accept': 'application/json' }
        };

        if (body) {
            if (isFormData) {
                options.body = body;
            } else {
                options.headers['Content-Type'] = 'application/json';
                options.body = JSON.stringify(body);
            }
        }

        if (buttonElement) toggleButtonLoading(buttonElement, true);
        updateStatus(`Sending ${method} request to ${API_BASE_URL}${endpoint}...`, false, true);
        try {
            const response = await fetch(`${API_BASE_URL}${endpoint}`, options);
            const data = await response.json();

            updateStatus(data.message || `Received response (Status: ${response.status})`, !response.ok, false);

            if (!response.ok) {
                throw new Error(data.message || `Request failed with status: ${response.status}`);
            }
            if (buttonElement) toggleButtonLoading(buttonElement, false);
            return data;
        } catch (error) {
            console.error(`API call to ${endpoint} failed:`, error);
            updateStatus(`API Error: ${error.message}`, true, false);
             if (buttonElement) toggleButtonLoading(buttonElement, false);
            throw error;
        }
    }

    configForm.elements.direction.forEach(radio => {
        radio.addEventListener('change', (event) => {
            const direction = event.target.value;
            updateStatus(`Direction changed to: ${direction}`, false);
            clearTableSelect();
            clearColumnList();
            clearPreview();
            updateResults('N/A');

            const isChSource = direction === 'ch_to_ff';

            chConfigFieldset.querySelector('legend').textContent = isChSource ? 'ClickHouse Source' : 'ClickHouse Target';
            ffConfigFieldset.querySelector('legend').textContent = isChSource ? 'Flat File Target' : 'Flat File Source';

            toggleElement(connectChBtn, isChSource);
            const loadColumnsGroup = loadColumnsBtn ? loadColumnsBtn.closest('.input-group') : null;
            toggleElement(loadColumnsGroup, isChSource);

            const ffPathGroup = document.getElementById('ff-path-group');
            const ffPathInput = ffPathGroup ? ffPathGroup.querySelector('input[name="ff_path"]') : null;
            let fileInput = ffConfigFieldset.querySelector('input[type="file"][name="ff_file"]');
            let fileDiv = fileInput ? fileInput.closest('.mb-3') : null;

            if (isChSource) {
                if (fileDiv) fileDiv.remove();
                if (ffPathGroup) toggleElement(ffPathGroup, true);
                if (ffPathInput) ffPathInput.required = true;

            } else {
                if (ffPathGroup) toggleElement(ffPathGroup, false);
                if (ffPathInput) ffPathInput.required = false;

                if (!fileInput) {
                    fileDiv = document.createElement('div');
                    fileDiv.classList.add('mb-3');
                    const labelElem = document.createElement('label');
                    labelElem.htmlFor = 'ff_file_input';
                    labelElem.classList.add('form-label');
                    labelElem.textContent = 'Upload CSV File:';
                    fileInput = document.createElement('input');
                    fileInput.type = 'file';
                    fileInput.name = 'ff_file';
                    fileInput.required = true;
                    fileInput.classList.add('form-control', 'form-control-sm');
                    fileInput.id = 'ff_file_input';
                    fileInput.accept = '.csv,text/csv';

                    fileDiv.appendChild(labelElem);
                    fileDiv.appendChild(fileInput);

                    const delimiterInput = ffConfigFieldset.querySelector('input[name="ff_delimiter"]');
                    if (delimiterInput && delimiterInput.parentElement) {
                         delimiterInput.parentElement.insertAdjacentElement('afterend', fileDiv);
                    } else {
                        ffConfigFieldset.appendChild(fileDiv);
                    }
                }
                 toggleElement(fileDiv, true);
            }
        });
    });

    connectChBtn.addEventListener('click', async () => {
        updateStatus('Connecting to ClickHouse and fetching tables...', false, true);
        clearTableSelect();
        clearColumnList();
        clearPreview();
        updateResults('N/A');

        const formData = new FormData(configForm);
        const connectionDetails = getClickHouseConnectionDetails(formData);

        if (!connectionDetails.host || !connectionDetails.port || !connectionDetails.database || !connectionDetails.user) {
            updateStatus('Please fill in ClickHouse Host, Port, Database, and User.', true);
            return;
        }

        try {
            const response = await makeApiCall('/clickhouse/connect', 'POST', connectionDetails, false, connectChBtn);
            if (response.success && response.data) {
                chTableSelect.innerHTML = '<option value="">-- Select Table --</option>';
                if (response.data.length === 0) {
                    updateStatus('Connected, but no tables found or accessible.', true);
                } else {
                    response.data.forEach(table => {
                        const option = document.createElement('option');
                        option.value = table;
                        option.textContent = table;
                        chTableSelect.appendChild(option);
                    });
                    updateStatus(response.message || 'Connected. Select a table.', false);
                }
                toggleElement(chTablesSection, true);
            } else {
                updateResults('Connection or table listing failed.');
                toggleElement(chTablesSection, false);
            }
        } catch (error) {
            updateResults('Connection or table listing failed.');
            toggleElement(chTablesSection, false);
        } finally {
             toggleButtonLoading(connectChBtn, false);
        }
    });

    loadColumnsBtn.addEventListener('click', async () => {
        const selectedTable = chTableSelect.value;
        if (!selectedTable) {
            updateStatus('Please select a table first.', true);
            return;
        }
        updateStatus(`Loading columns for table: ${selectedTable}...`, false, true);
        clearColumnList();
        clearPreview();
        updateResults('N/A');

        const formData = new FormData(configForm);
        const connectionDetails = getClickHouseConnectionDetails(formData);
        const requestBody = { ...connectionDetails, table: selectedTable };

        try {
            const response = await makeApiCall('/clickhouse/columns', 'POST', requestBody, false, loadColumnsBtn);
            if (response.success && response.data) {
                columnsListDiv.innerHTML = '';
                if (response.data.length === 0) {
                    updateStatus(`Warning: Table '${selectedTable}' has no columns or columns could not be retrieved.`, true);
                    toggleElement(columnSelectionSection, false);
                    toggleElement(previewBtn, false);
                } else {
                    response.data.forEach((col, index) => {
                        const div = document.createElement('div');
                        div.classList.add('form-check');
                        const checkbox = document.createElement('input');
                        checkbox.type = 'checkbox';
                        checkbox.name = 'selected_columns';
                        checkbox.value = col;
                        checkbox.checked = true;
                        checkbox.classList.add('form-check-input');
                        const checkId = `col-check-${index}`;
                        checkbox.id = checkId;
                        const label = document.createElement('label');
                        label.classList.add('form-check-label');
                        label.htmlFor = checkId;
                        label.appendChild(document.createTextNode(col));
                        div.appendChild(checkbox);
                        div.appendChild(label);
                        columnsListDiv.appendChild(div);
                    });
                    updateStatus(response.message || 'Columns loaded. Select columns to ingest.', false);
                    toggleElement(columnSelectionSection, true);
                    toggleElement(previewBtn, true);
                }
            } else {
                updateResults('Failed to load columns.');
                toggleElement(columnSelectionSection, false);
                toggleElement(previewBtn, false);
            }
        } catch (error) {
            updateResults('Failed to load columns.');
            toggleElement(columnSelectionSection, false);
            toggleElement(previewBtn, false);
        } finally {
            toggleButtonLoading(loadColumnsBtn, false);
        }
    });

    previewBtn.addEventListener('click', async () => {
        updateStatus('Fetching data preview...', false, true);
        clearPreview();
        updateResults('N/A');

        const formData = new FormData(configForm);
        const selectedColumns = Array.from(formData.getAll('selected_columns'));
        const selectedTable = formData.get('ch_table');

        if (!selectedTable) {
            updateStatus('No table selected for preview.', true); return;
        }
        if (selectedColumns.length === 0) {
            updateStatus('Please select at least one column to preview.', true); return;
        }

        const connectionDetails = getClickHouseConnectionDetails(formData);
        const requestBody = { ...connectionDetails, table: selectedTable, columns: selectedColumns };

        try {
            const response = await makeApiCall('/clickhouse/preview', 'POST', requestBody, false, previewBtn);
            if (response.success && response.data) {
                const preview = response.data;
                if (!preview.headers || !preview.rows) {
                    throw new Error("Invalid preview data format received.");
                }

                previewTableHead.innerHTML = '';
                const headerRow = document.createElement('tr');
                preview.headers.forEach(header => {
                    const th = document.createElement('th');
                    th.scope = 'col';
                    th.textContent = header;
                    headerRow.appendChild(th);
                });
                previewTableHead.appendChild(headerRow);

                previewTableBody.innerHTML = '';
                if (preview.rows.length === 0) {
                    const singleCellRow = document.createElement('tr');
                    const td = document.createElement('td');
                    td.textContent = "No data returned for preview.";
                    td.colSpan = preview.headers.length || 1;
                    td.style.textAlign = 'center';
                    singleCellRow.appendChild(td);
                    previewTableBody.appendChild(singleCellRow);
                } else {
                    preview.rows.forEach(row => {
                        const bodyRow = document.createElement('tr');
                        preview.headers.forEach((_, index) => {
                            const td = document.createElement('td');
                            const cellData = row[index];
                            td.textContent = cellData === null || cellData === undefined ? 'NULL' : String(cellData);
                            bodyRow.appendChild(td);
                        });
                        previewTableBody.appendChild(bodyRow);
                    });
                }
                updateStatus(response.message || `Data preview loaded (${preview.rows.length} rows).`, false);
                toggleElement(previewDataSection, true);
            } else {
                updateResults('Failed to load preview.');
                toggleElement(previewDataSection, false);
            }
        } catch (error) {
            updateResults('Failed to load preview.');
            toggleElement(previewDataSection, false);
        } finally {
            toggleButtonLoading(previewBtn, false);
        }
    });

    configForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        updateStatus('Starting ingestion process...', false, true);
        clearPreview();
        updateResults('N/A');

        const formDataRaw = new FormData(configForm);
        const direction = formDataRaw.get('direction');
        const selectedColumns = Array.from(formDataRaw.getAll('selected_columns'));
        const connectionDetails = getClickHouseConnectionDetails(formDataRaw);

        let requestConfig = { 
            direction: direction,
            chHost: connectionDetails.host,
            chPort: connectionDetails.port,
            chDatabase: connectionDetails.database,
            chUser: connectionDetails.user,
            chToken: connectionDetails.token,
            chTable: formDataRaw.get('ch_table'),
            ffPath: formDataRaw.get('ff_path'),
            ffDelimiter: formDataRaw.get('ff_delimiter') || ',',
            columns: selectedColumns
        };

        let isFormDataUpload = false;
        let bodyToSend = requestConfig;

        if (direction === 'ch_to_ff') {
            if (!requestConfig.chHost || !requestConfig.chPort || !requestConfig.chDatabase || !requestConfig.chUser) {
                updateStatus('ClickHouse source connection details missing.', true); return;
            }
            if (!requestConfig.chTable) {
                updateStatus('ClickHouse source table not selected.', true); return;
            }
            if (!requestConfig.ffPath) {
                updateStatus('Target flat file path/name is missing.', true); return;
            }
             if (requestConfig.columns.length === 0) {
                updateStatus('Warning: No columns selected, all columns will be ingested.', false);
             }
            bodyToSend = requestConfig;
            isFormDataUpload = false;

        } else if (direction === 'ff_to_ch') {
             if (!requestConfig.chHost || !requestConfig.chPort || !requestConfig.chDatabase || !requestConfig.chUser) {
                updateStatus('ClickHouse target connection details missing.', true); return;
            }
            if (!requestConfig.chTable) {
                updateStatus('ClickHouse target table not specified.', true); return;
            }
            const fileInput = configForm.querySelector('input[type="file"][name="ff_file"]');
            if (!fileInput || fileInput.files.length === 0) {
                updateStatus('No source flat file selected for upload.', true); return;
            }
             if (!requestConfig.ffDelimiter) {
                updateStatus('Flat file delimiter is missing.', true); return;
            }

            const uploadFormData = new FormData();
            Object.keys(requestConfig).forEach(key => {
                const value = requestConfig[key];
                if (value !== null && value !== undefined) {
                     if (key === 'columns' && Array.isArray(value)) {
                         value.forEach(col => uploadFormData.append('columns', col));
                     } else if (!Array.isArray(value)) { 
                        uploadFormData.append(key, String(value));
                     }
                }
            });
             uploadFormData.append('file', fileInput.files[0]);

             bodyToSend = uploadFormData;
             isFormDataUpload = true;

        } else {
            updateStatus('Invalid ingestion direction selected.', true); return;
        }

        try {
            const response = await makeApiCall('/ingest', 'POST', bodyToSend, isFormDataUpload, document.getElementById('start-ingestion-btn'));
            if (response.success && response.data) {
                const count = response.data.recordsProcessed !== undefined ? response.data.recordsProcessed : 'N/A';
                updateResults(`Total records processed: ${count}`, true);
            } else {
                updateResults('Ingestion failed.');
            }
        } catch (error) {
            updateResults('Ingestion failed.');
        }
    });

    if (selectAllColsBtn) {
        selectAllColsBtn.addEventListener('click', () => {
            columnsListDiv.querySelectorAll('input[type="checkbox"]').forEach(cb => cb.checked = true);
        });
    }
    if (deselectAllColsBtn) {
        deselectAllColsBtn.addEventListener('click', () => {
            columnsListDiv.querySelectorAll('input[type="checkbox"]').forEach(cb => cb.checked = false);
        });
    }

    updateStatus('Application loaded. Configure the ingestion.', false);
    clearTableSelect();
    clearColumnList();
    clearPreview();
    updateResults('N/A');
    const initialDirectionRadio = configForm.querySelector('input[name="direction"]:checked');
    if (initialDirectionRadio) {
        initialDirectionRadio.dispatchEvent(new Event('change'));
    } else {
        console.warn("No initial direction selected.");
    }

});