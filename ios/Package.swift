// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "XiaohonghuaCore",
    platforms: [.macOS(.v13)],
    products: [.library(name: "XiaohonghuaCore", targets: ["XiaohonghuaCore"])],
    targets: [
        .target(name: "XiaohonghuaCore", path: "Xiaohonghua/Core"),
        .testTarget(name: "CoreTests", dependencies: ["XiaohonghuaCore"], path: "Tests/CoreTests", resources: [.process("Fixtures")])
    ]
)
