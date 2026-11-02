import AnchorShared
import SwiftUI

/// Time signature picker: common presets plus a custom beats-per-bar / subdivision editor.
struct TimeSignatureView: View {
    let state: MetronomeUiState
    let app: AnchorIosApp
    let accent: Color

    @Environment(\.dismiss) private var dismiss
    @State private var customBeats: Int = 4
    @State private var customSubdivision: String = "QUARTER"

    private let columns = [GridItem(.adaptive(minimum: 72), spacing: 10)]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Presets")
                        .font(.headline)
                    LazyVGrid(columns: columns, spacing: 10) {
                        ForEach(app.timeSignaturePresets, id: \.name) { preset in
                            let isSelected = Int(preset.beats) == Int(state.totalBeats)
                                && preset.subdivision == state.currentSubdivision
                            Button {
                                app.selectTimeSignature(preset: preset)
                                dismiss()
                            } label: {
                                Text(preset.displayName)
                                    .font(.title3.weight(.bold).monospacedDigit())
                                    .frame(maxWidth: .infinity, minHeight: 56)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(isSelected ? accent.opacity(0.25) : Color(.secondarySystemBackground))
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    Text("Custom")
                        .font(.headline)
                    Stepper(value: $customBeats, in: 1...Int(app.maxBeats)) {
                        Text("Beats per bar: \(customBeats)")
                            .monospacedDigit()
                    }
                    Picker("Subdivision", selection: $customSubdivision) {
                        ForEach(app.subdivisions, id: \.name) { sub in
                            Text("\(sub.symbol) \(sub.displayName)").tag(sub.name)
                        }
                    }
                    .pickerStyle(.segmented)
                    Button {
                        if let sub = app.subdivisions.first(where: { $0.name == customSubdivision }) {
                            app.changeTimeSignature(beats: Int32(customBeats), subdivision: sub)
                        }
                        dismiss()
                    } label: {
                        Text("Apply \(customBeats) beats")
                            .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(16)
            }
            .navigationTitle("Time Signature")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .onAppear {
                customBeats = Int(state.totalBeats)
                customSubdivision = state.currentSubdivision.name
            }
        }
        .tint(accent)
    }
}
