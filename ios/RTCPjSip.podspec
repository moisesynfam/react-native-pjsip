require 'json'

package = JSON.parse(File.read(File.join(__dir__, '../package.json')))

Pod::Spec.new do |s|
  s.name                = "RTCPjSip"
  s.version             = package['version']
  s.summary             = package['description']
  s.homepage            = package['homepage']
  s.license             = package['license']
  s.author              = package['author']
  s.source              = { :git => 'https://github.com/moisesynfam/react-native-pjsip.git' }
  s.requires_arc        = true
  s.platform            = :ios, "8.0"
  # s.preserve_paths      = "ios/*.framework"
  s.source_files        = "RTCPjSip/*.{h,m}"
  s.vendored_frameworks = "VialerPJSIP.framework"
  s.dependency 'React'
  # s.dependency 'Vialer-pjsip-iOS'
  s.xcconfig = {'GCC_PREPROCESSOR_DEFINITIONS' => '$(inherited) COCOAPODS=1 IOS=1' }
end