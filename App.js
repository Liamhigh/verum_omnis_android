import React, { useState } from "react";
import { SafeAreaView, View, Text, TextInput, Pressable, ScrollView } from "react-native";
import Constants from "expo-constants";

const API = Constants.expoConfig?.extra?.API_BASE;

export default function App() {
  const [fileName, setFileName] = useState("sample.pdf");
  const [shaOrig, setShaOrig] = useState("a".repeat(64));
  const [shaPdf, setShaPdf] = useState("b".repeat(64));
  const [result, setResult] = useState("");

  async function verify() {
    setResult("Submitting…");
    try {
      const body = {
        manifest: {
          version: "verum_v1.0.0",
          manifest_id: "demo-" + Date.now(),
          file_name: fileName,
          sha512_original: shaOrig,
          sha512_pdf: shaPdf,
          sealed_timestamp_utc: new Date().toISOString(),
          device_public_key: "DEMO_PUBKEY",
          device_id_fingerprint: "DEMO_FP",
          local_forensic_flags: { image_resampling_suspect: true },
          signature: "DEMO_SIG"
        },
        redacted_preview: { first_lines: "Demo preview" }
      };

      const r = await fetch(`${API}/verify-manifest`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      const json = await r.json();
      setResult(JSON.stringify(json, null, 2));
    } catch (e) {
      setResult("Error: " + e.message);
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: "#0b0b0c" }}>
      <ScrollView contentContainerStyle={{ padding: 20 }}>
        <Text style={{ color: "white", fontSize: 22, fontWeight: "700" }}>Verum Omnis – Android</Text>
        <Text style={{ color: "#9aa0a6", marginBottom: 14 }}>
          Sends a sealed-manifest to your Cloud Run API and shows the advisory JSON.
        </Text>

        <Text style={{ color: "#e8eaed" }}>File name</Text>
        <TextInput style={s.input} value={fileName} onChangeText={setFileName} />

        <Text style={{ color: "#e8eaed", marginTop: 10 }}>sha512_original (64+ hex)</Text>
        <TextInput style={s.input} value={shaOrig} onChangeText={setShaOrig} autoCapitalize="none" />

        <Text style={{ color: "#e8eaed", marginTop: 10 }}>sha512_pdf (64+ hex)</Text>
        <TextInput style={s.input} value={shaPdf} onChangeText={setShaPdf} autoCapitalize="none" />

        <Pressable onPress={verify} style={s.btn}>
          <Text style={{ color: "white", fontWeight: "700" }}>Submit for Review</Text>
        </Pressable>

        <Text style={{ color: "#9aa0a6", marginTop: 20 }}>Result</Text>
        <View style={s.resultBox}>
          <Text selectable style={{ color: "#e8eaed", fontFamily: "monospace" }}>{result || "…"}</Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const s = {
  input: { backgroundColor: "#1f1f20", color: "white", padding: 10, borderRadius: 10, marginTop: 6 },
  btn: { backgroundColor: "#2563eb", padding: 14, borderRadius: 12, alignItems: "center", marginTop: 16 },
  resultBox: { backgroundColor: "#111113", padding: 12, borderRadius: 10, minHeight: 140, marginTop: 8 }
};
