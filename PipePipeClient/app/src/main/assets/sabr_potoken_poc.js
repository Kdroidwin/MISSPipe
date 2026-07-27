/*
 * SABR PO token POC — WebView pipeline (att mode).
 *
 * Ported from the local research mint (mint-po-token-browser.mjs), adapted to run inside an Android
 * WebView instead of puppeteer. It must be injected AFTER https://www.youtube.com/ has finished
 * loading (same-origin is required: att/get and GenerateIT are youtube.com endpoints, and the
 * BotGuard interpreter is embedded in the challenge).
 *
 * Flow: read browser context -> att/get challenge -> run BotGuard VM -> snapshot -> GenerateIT
 * integrity token -> mint a videoId-bound PO token -> hand the result back through the
 * `SabrPocBridge` JavascriptInterface.
 *
 * INTERNAL / LOCAL POC ONLY. The minted PO token is session-bound; keep it out of any public log.
 * API_KEY / REQUEST_KEY are the well-known public ecosystem constants, not secrets.
 */
(function () {
    'use strict';

    // YouTube ships a Trusted Types CSP (require-trusted-types-for 'script') that blocks
    // new Function()/eval of the BotGuard interpreter. Installing an identity "default" policy makes
    // Chromium route those sinks through it, restoring dynamic evaluation. If the CSP forbids
    // creating the policy, loadBotGuard() will surface the eval error instead.
    try {
        if (window.trustedTypes && window.trustedTypes.createPolicy
                && !window.trustedTypes.defaultPolicy) {
            window.trustedTypes.createPolicy('default', {
                createHTML: function (value) { return value; },
                createScript: function (value) { return value; },
                createScriptURL: function (value) { return value; }
            });
        }
    } catch (ttError) {
        // ignore; surfaced later as an eval failure
    }

    var API_KEY = 'AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw';
    var REQUEST_KEY = 'O43z0dpjhgX20SCx4KAo';

    function report(result) {
        try {
            // eslint-disable-next-line no-undef
            SabrPocBridge.onResult(JSON.stringify(result));
        } catch (e) {
            // Bridge not present (e.g. plain browser run): fall back to console.
            try {
                console.log('[sabr-poc] ' + JSON.stringify(result));
            } catch (ignored) {
                // nothing else we can do
            }
        }
    }

    function step(message) {
        try { console.log('[sabr-poc] ' + message); } catch (e) { /* ignore */ }
    }

    function readVisitorData() {
        var cfg = window.ytcfg;
        var fromCfg = cfg && typeof cfg.get === 'function' ? cfg.get('VISITOR_DATA') : null;
        if (fromCfg) {
            return fromCfg;
        }
        var html = document.documentElement.innerHTML;
        var marker = '"VISITOR_DATA":"';
        var start = html.indexOf(marker);
        if (start < 0) {
            throw new Error('Could not find visitor data');
        }
        var from = start + marker.length;
        var end = html.indexOf('"', from);
        if (end < 0) {
            throw new Error('Could not find visitor data end');
        }
        return html.slice(from, end);
    }

    function readClientVersion() {
        var cfg = window.ytcfg;
        var fromCfg = cfg && typeof cfg.get === 'function'
            ? cfg.get('INNERTUBE_CLIENT_VERSION') : null;
        return fromCfg || '2.20260114.01.00';
    }

    function normalizeTrustedUrl(value) {
        if (!value) {
            throw new Error('Missing interpreter url');
        }
        return value.indexOf('//') === 0 ? 'https:' + value : value;
    }

    function fetchChallenge(ctx) {
        var context = {
            client: {
                clientName: 'WEB',
                clientVersion: ctx.clientVersion,
                hl: 'en',
                gl: 'US',
                utcOffsetMinutes: 0,
                visitorData: ctx.visitorData
            }
        };
        return fetch('https://www.youtube.com/youtubei/v1/att/get?prettyPrint=false&alt=json', {
            method: 'POST',
            headers: {
                'Accept': '*/*',
                'Content-Type': 'application/json',
                'X-Goog-Visitor-Id': ctx.visitorData,
                'X-Youtube-Client-Version': ctx.clientVersion,
                'X-Youtube-Client-Name': '1'
            },
            body: JSON.stringify({
                engagementType: 'ENGAGEMENT_TYPE_UNBOUND',
                context: context
            })
        }).then(function (response) {
            return response.json().then(function (data) {
                if (!response.ok || !data.bgChallenge) {
                    throw new Error('att/get failed status=' + response.status);
                }
                return data.bgChallenge;
            });
        });
    }

    function resolveInterpreter(challenge, userAgent) {
        var embedded = challenge.interpreterJavascript
            && challenge.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue;
        if (embedded) {
            return Promise.resolve(embedded);
        }
        var url = normalizeTrustedUrl(
            (challenge.interpreterJavascript
                && challenge.interpreterJavascript
                    .privateDoNotAccessOrElseTrustedResourceUrlWrappedValue)
            || (challenge.interpreterUrl
                && challenge.interpreterUrl
                    .privateDoNotAccessOrElseTrustedResourceUrlWrappedValue));
        return fetch(url, { headers: { 'User-Agent': userAgent } }).then(function (response) {
            return response.text().then(function (js) {
                if (!response.ok || !js) {
                    throw new Error('interpreter fetch failed status=' + response.status);
                }
                return js;
            });
        });
    }

    function loadBotGuard(interpreterJavascript, program, globalName) {
        return new Promise(function (resolve, reject) {
            try {
                new Function(interpreterJavascript)();
            } catch (e) {
                reject(new Error('interpreter eval failed: ' + e.message));
                return;
            }
            var vm = window[globalName];
            if (!vm || typeof vm.a !== 'function') {
                reject(new Error('BotGuard VM missing init function'));
                return;
            }
            var timeout = setTimeout(function () {
                reject(new Error('BotGuard init timeout'));
            }, 10000);
            try {
                vm.a(program, function (asyncSnapshotFunction) {
                    clearTimeout(timeout);
                    resolve({ asyncSnapshotFunction: asyncSnapshotFunction });
                }, true, undefined, function () { }, [[], []]);
            } catch (e) {
                clearTimeout(timeout);
                reject(new Error('BotGuard init threw: ' + e.message));
            }
        });
    }

    function snapshot(functions, webPoSignalOutput) {
        return new Promise(function (resolve, reject) {
            var timeout = setTimeout(function () {
                reject(new Error('BotGuard snapshot timeout'));
            }, 10000);
            functions.asyncSnapshotFunction(function (response) {
                clearTimeout(timeout);
                resolve(response);
            }, [undefined, undefined, webPoSignalOutput, undefined]);
        });
    }

    function fetchIntegrityToken(botGuardResponse, userAgent) {
        return fetch('https://www.youtube.com/api/jnn/v1/GenerateIT', {
            method: 'POST',
            headers: {
                'content-type': 'application/json+protobuf',
                'x-goog-api-key': API_KEY,
                'x-user-agent': 'grpc-web-javascript/0.1',
                'User-Agent': userAgent
            },
            body: JSON.stringify([REQUEST_KEY, botGuardResponse])
        }).then(function (response) {
            return response.json().then(function (data) {
                var integrityToken = data[0];
                if (typeof integrityToken !== 'string') {
                    throw new Error('GenerateIT failed status=' + response.status);
                }
                return integrityToken;
            });
        });
    }

    function base64ToU8(value) {
        var normalized = value.replace(/-/g, '+').replace(/_/g, '/');
        var padded = normalized + '==='.slice((normalized.length + 3) % 4);
        var binary = atob(padded);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) {
            bytes[i] = binary.charCodeAt(i);
        }
        return bytes;
    }

    function u8ToBase64Url(value) {
        var binary = '';
        for (var i = 0; i < value.length; i++) {
            binary += String.fromCharCode(value[i]);
        }
        return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
    }

    function mint(webPoSignalOutput, integrityToken, identifier) {
        var getMinter = webPoSignalOutput[0];
        if (typeof getMinter !== 'function') {
            return Promise.reject(new Error('Missing PO minter factory'));
        }
        return Promise.resolve(getMinter(base64ToU8(integrityToken))).then(function (mintCallback) {
            if (typeof mintCallback !== 'function') {
                throw new Error('Missing PO mint callback');
            }
            return Promise.resolve(mintCallback(new TextEncoder().encode(identifier)))
                .then(u8ToBase64Url);
        });
    }

    function run() {
        step('run start, readyState=' + document.readyState + ' origin=' + location.origin);
        var videoId = window.__SABR_POC_VIDEO_ID || 'aqz-KE-bpKQ';
        var ctx = {
            visitorData: readVisitorData(),
            clientVersion: readClientVersion(),
            userAgent: navigator.userAgent
        };
        step('context ok visitorLen=' + ctx.visitorData.length + ' clientVersion=' + ctx.clientVersion);
        var webPoSignalOutput = [];
        var integrityTokenLength = -1;
        step('fetching att/get challenge...');
        return fetchChallenge(ctx).then(function (challenge) {
            step('challenge ok embedded='
                + !!(challenge.interpreterJavascript
                    && challenge.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue));
            return resolveInterpreter(challenge, ctx.userAgent).then(function (interpreterJs) {
                step('interpreter resolved len=' + (interpreterJs ? interpreterJs.length : -1));
                return loadBotGuard(interpreterJs, challenge.program, challenge.globalName);
            });
        }).then(function (functions) {
            step('botguard loaded, taking snapshot...');
            return snapshot(functions, webPoSignalOutput);
        }).then(function (botGuardResponse) {
            step('snapshot ok, calling GenerateIT...');
            return fetchIntegrityToken(botGuardResponse, ctx.userAgent);
        }).then(function (integrityToken) {
            step('integrity token len=' + integrityToken.length + ', minting...');
            integrityTokenLength = integrityToken.length;
            return mint(webPoSignalOutput, integrityToken, videoId);
        }).then(function (poToken) {
            report({
                ok: true,
                videoId: videoId,
                clientVersion: ctx.clientVersion,
                visitorDataLength: ctx.visitorData.length,
                integrityTokenLength: integrityTokenLength,
                poTokenLength: poToken.length,
                poToken: poToken,
                userAgent: ctx.userAgent
            });
        });
    }

    function reportError(e) {
        report({
            ok: false,
            error: (e && e.message) ? e.message : String(e),
            errorName: e && e.name ? e.name : '',
            stack: e && e.stack ? String(e.stack).slice(0, 400) : '',
            userAgent: navigator.userAgent
        });
    }

    try {
        run().catch(reportError);
    } catch (e) {
        reportError(e);
    }
})();
