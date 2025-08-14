// "use client"
//
// import { useState, useEffect } from "react"
// import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
// import { Badge } from "@/components/ui/badge"
// import { Button } from "@/components/ui/button"
// import { RefreshCw, CheckCircle, Mail, CreditCard, Calendar } from "lucide-react"
// import { useToast } from "@/hooks/use-toast"
//
// interface ProcessedPayment {
//   paymentId: string
//   paymentTs: string
//   status: string
//   method: string
//   messageId: string
//   subject: string
//   payerEmail: string
//   phone: string
//   amount: string
//   merchantName: string
// }
//
// interface ProcessedEntry {
//   key: string
//   type: string
//   value: string
//   payment: ProcessedPayment | null
// }
//
// interface ProcessedPaymentsResponse {
//   pattern: string
//   limit: number
//   found: number
//   entries: ProcessedEntry[]
// }
//
// export function ProcessedPayments() {
//   const [entries, setEntries] = useState<ProcessedEntry[]>([])
//   const [loading, setLoading] = useState(true)
//   const [lastUpdated, setLastUpdated] = useState<Date>(new Date())
//   const { toast } = useToast()
//
//   const fetchProcessedPayments = async () => {
//     try {
//       const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"
//       const response = await fetch(`${baseUrl}/api/admin/processed`)
//
//       if (!response.ok) {
//         throw new Error("Failed to fetch processed payments")
//       }
//
//       const data: ProcessedPaymentsResponse = await response.json()
//       setEntries(data.entries || [])
//       setLastUpdated(new Date())
//     } catch (error) {
//       console.error("Error fetching processed payments:", error)
//       toast({
//         title: "Error",
//         description: "Failed to fetch processed payments. Please check your connection.",
//         variant: "destructive",
//       })
//     } finally {
//       setLoading(false)
//     }
//   }
//
//   useEffect(() => {
//     fetchProcessedPayments()
//     const interval = setInterval(fetchProcessedPayments, 30000) // Refresh every 30 seconds
//     return () => clearInterval(interval)
//   }, [])
//
//   const formatDate = (dateString: string) => {
//     try {
//       return new Date(dateString).toLocaleString()
//     } catch {
//       return dateString
//     }
//   }
//
//   const validPayments = entries.filter((entry) => entry.value !== null)
//
//   return (
//     <div className="space-y-6">
//       <div className="flex items-center justify-between">
//         <div>
//           <h2 className="text-2xl font-bold text-slate-800">Processed Payments</h2>
//           <p className="text-slate-600">Keep track of completed payments</p>
//         </div>
//         <div className="flex items-center gap-4">
//           <div className="text-sm text-slate-500">Last updated: {lastUpdated.toLocaleTimeString()}</div>
//           <Button
//             onClick={fetchProcessedPayments}
//             disabled={loading}
//             size="sm"
//             className="bg-gradient-to-r from-purple-600 to-purple-800 hover:from-purple-700 hover:to-purple-900"
//           >
//             <RefreshCw className={`h-4 w-4 mr-2 ${loading ? "animate-spin" : ""}`} />
//             Refresh
//           </Button>
//         </div>
//       </div>
//
//       {loading && entries.length === 0 ? (
//         <div className="flex items-center justify-center py-12">
//           <div className="text-center">
//             <RefreshCw className="h-8 w-8 animate-spin text-purple-600 mx-auto mb-4" />
//             <p className="text-slate-600">Loading processed payments...</p>
//           </div>
//         </div>
//       ) : validPayments.length === 0 ? (
//         <Card className="border-purple-100">
//           <CardContent className="flex items-center justify-center py-12">
//             <div className="text-center">
//               <CheckCircle className="h-12 w-12 text-slate-400 mx-auto mb-4" />
//               <h3 className="text-lg font-medium text-slate-800 mb-2">No Processed Payments</h3>
//               <p className="text-slate-600">There are currently no processed payment records.</p>
//             </div>
//           </CardContent>
//         </Card>
//       ) : (
//         <div className="space-y-4">
//           <div className="text-sm text-slate-600 mb-4">
//             Showing {validPayments.length} of {entries.length} total entries
//           </div>
//
//           <div className="grid gap-4">
//             {validPayments.map((entry, index) => {
//               const payment = entry.payment!
//               return (
//                 <Card key={entry.key || index} className="border-purple-100 hover:shadow-lg transition-shadow">
//                   <CardHeader>
//                     <div className="flex items-center justify-between">
//                       <CardTitle className="text-lg text-slate-800">{payment.subject || "Processed Payment"}</CardTitle>
//                       <Badge variant="secondary" className="bg-green-100 text-green-700">
//                         <CheckCircle className="h-3 w-3 mr-1" />
//                         Processed
//                       </Badge>
//                     </div>
//                     <CardDescription className="flex items-center gap-2">
//                       <Calendar className="h-4 w-4" />
//                       {formatDate(payment.paymentTs)}
//                     </CardDescription>
//                   </CardHeader>
//                   <CardContent>
//                     <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
//                       <div className="space-y-2">
//                         <div className="flex items-center gap-2 text-sm">
//                           <Mail className="h-4 w-4 text-slate-500" />
//                           <span className="font-medium">Email:</span>
//                         </div>
//                         <p className="text-sm text-slate-700 pl-6">{payment.payerEmail || "Not provided"}</p>
//                       </div>
//
//                       <div className="space-y-2">
//                         <div className="flex items-center gap-2 text-sm">
//                           <CreditCard className="h-4 w-4 text-slate-500" />
//                           <span className="font-medium">Amount:</span>
//                         </div>
//                         <p className="text-sm text-slate-700 pl-6">
//                           {payment.amount ? `₹${payment.amount}` : "Not specified"}
//                         </p>
//                       </div>
//
//                       <div className="space-y-2">
//                         <div className="flex items-center gap-2 text-sm">
//                           <CreditCard className="h-4 w-4 text-slate-500" />
//                           <span className="font-medium">Method:</span>
//                         </div>
//                         <p className="text-sm text-slate-700 pl-6">{payment.method || "Not specified"}</p>
//                       </div>
//                     </div>
//
//                     <div className="mt-4 grid grid-cols-1 lg:grid-cols-2 gap-4">
//                       {payment.paymentId && (
//                         <div className="p-3 bg-slate-50 rounded-lg">
//                           <p className="text-xs text-slate-500 mb-1">Payment ID</p>
//                           <p className="text-sm font-mono text-slate-700">{payment.paymentId}</p>
//                         </div>
//                       )}
//
//                       {payment.merchantName && (
//                         <div className="p-3 bg-slate-50 rounded-lg">
//                           <p className="text-xs text-slate-500 mb-1">Merchant</p>
//                           <p className="text-sm text-slate-700">{payment.merchantName}</p>
//                         </div>
//                       )}
//                     </div>
//                   </CardContent>
//                 </Card>
//               )
//             })}
//           </div>
//         </div>
//       )}
//     </div>
//   )
// }
"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { RefreshCw, CheckCircle, Mail, CreditCard, Calendar, XCircle } from "lucide-react"
import { useToast } from "@/hooks/use-toast"

interface ProcessedPayment {
  paymentId: string
  paymentTs: string
  status: string
  method: string
  messageId: string
  subject: string
  payerEmail: string
  phone: string
  amount: string
  merchantName: string
}

interface ProcessedEntry {
  key: string
  type: string
  value: string
  payment: ProcessedPayment | null
}

interface ProcessedPaymentsResponse {
  pattern: string
  limit: number
  found: number
  entries: ProcessedEntry[]
}

export function ProcessedPayments() {
  const [entries, setEntries] = useState<ProcessedEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date())
  const { toast } = useToast()

  const fetchProcessedPayments = async () => {
    try {
      const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"
      const response = await fetch(`${baseUrl}/api/admin/processed`)

      if (!response.ok) {
        throw new Error("Failed to fetch processed payments")
      }

      const data: ProcessedPaymentsResponse = await response.json()
      setEntries(data.entries || [])
      setLastUpdated(new Date())
    } catch (error) {
      console.error("Error fetching processed payments:", error)
      toast({
        title: "Error",
        description: "Failed to fetch processed payments. Please check your connection.",
        variant: "destructive",
      })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchProcessedPayments()
    const interval = setInterval(fetchProcessedPayments, 30000) // Refresh every 30 seconds
    return () => clearInterval(interval)
  }, [])

  const formatDate = (dateString: string) => {
    try {
      return new Date(dateString).toLocaleString()
    } catch {
      return dateString
    }
  }

  const claimedEntries = entries.filter((entry) => entry.payment === null)
  const notYetClaimedEntries = entries.filter((entry) => entry.payment !== null)

  return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-2xl font-bold text-slate-800">Processed Payments</h2>
            <p className="text-slate-600">Keep track of completed payments</p>
          </div>
          <div className="flex items-center gap-4">
            <div className="text-sm text-slate-500">Last updated: {lastUpdated.toLocaleTimeString()}</div>
            <Button
                onClick={fetchProcessedPayments}
                disabled={loading}
                size="sm"
                className="bg-gradient-to-r from-purple-600 to-purple-800 hover:from-purple-700 hover:to-purple-900"
            >
              <RefreshCw className={`h-4 w-4 mr-2 ${loading ? "animate-spin" : ""}`} />
              Refresh
            </Button>
          </div>
        </div>

        {loading && entries.length === 0 ? (
            <div className="flex items-center justify-center py-12">
              <div className="text-center">
                <RefreshCw className="h-8 w-8 animate-spin text-purple-600 mx-auto mb-4" />
                <p className="text-slate-600">Loading processed payments...</p>
              </div>
            </div>
        ) : entries.length === 0 ? (
            <Card className="border-purple-100">
              <CardContent className="flex items-center justify-center py-12">
                <div className="text-center">
                  <CheckCircle className="h-12 w-12 text-slate-400 mx-auto mb-4" />
                  <h3 className="text-lg font-medium text-slate-800 mb-2">No Processed Payments</h3>
                  <p className="text-slate-600">There are currently no processed payment records.</p>
                </div>
              </CardContent>
            </Card>
        ) : (
            <div className="space-y-8">
              {notYetClaimedEntries.length > 0 && (
                  <div className="space-y-4">
                    <div className="flex items-center gap-3">
                      <h3 className="text-xl font-semibold text-slate-800">Not Yet Claimed</h3>
                      <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-200">
                        {notYetClaimedEntries.length} payments
                      </Badge>
                    </div>

                    <div className="grid gap-4">
                      {notYetClaimedEntries.map((entry, index) => {
                        const payment = entry.payment!
                        return (
                            <Card key={entry.key || index} className="border-purple-100 hover:shadow-lg transition-shadow">
                              <CardHeader>
                                <div className="flex items-center justify-between">
                                  <CardTitle className="text-lg text-slate-800">
                                    {payment.subject || "Processed Payment"}
                                  </CardTitle>
                                  <Badge variant="secondary" className="bg-green-100 text-green-700">
                                    <CheckCircle className="h-3 w-3 mr-1" />
                                    Processed
                                  </Badge>
                                </div>
                                <CardDescription className="flex items-center gap-2">
                                  <Calendar className="h-4 w-4" />
                                  {formatDate(payment.paymentTs)}
                                </CardDescription>
                              </CardHeader>
                              <CardContent>
                                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                  <div className="space-y-2">
                                    <div className="flex items-center gap-2 text-sm">
                                      <Mail className="h-4 w-4 text-slate-500" />
                                      <span className="font-medium">Email:</span>
                                    </div>
                                    <p className="text-sm text-slate-700 pl-6">{payment.payerEmail || "Not provided"}</p>
                                  </div>

                                  <div className="space-y-2">
                                    <div className="flex items-center gap-2 text-sm">
                                      <CreditCard className="h-4 w-4 text-slate-500" />
                                      <span className="font-medium">Amount:</span>
                                    </div>
                                    <p className="text-sm text-slate-700 pl-6">
                                      {payment.amount ? `₹${payment.amount}` : "Not specified"}
                                    </p>
                                  </div>

                                  <div className="space-y-2">
                                    <div className="flex items-center gap-2 text-sm">
                                      <CreditCard className="h-4 w-4 text-slate-500" />
                                      <span className="font-medium">Method:</span>
                                    </div>
                                    <p className="text-sm text-slate-700 pl-6">{payment.method || "Not specified"}</p>
                                  </div>
                                </div>

                                <div className="mt-4 grid grid-cols-1 lg:grid-cols-2 gap-4">
                                  {payment.paymentId && (
                                      <div className="p-3 bg-slate-50 rounded-lg">
                                        <p className="text-xs text-slate-500 mb-1">Payment ID</p>
                                        <p className="text-sm font-mono text-slate-700">{payment.paymentId}</p>
                                      </div>
                                  )}

                                  {payment.merchantName && (
                                      <div className="p-3 bg-slate-50 rounded-lg">
                                        <p className="text-xs text-slate-500 mb-1">Merchant</p>
                                        <p className="text-sm text-slate-700">{payment.merchantName}</p>
                                      </div>
                                  )}
                                </div>
                              </CardContent>
                            </Card>
                        )
                      })}
                    </div>
                  </div>
              )}

              {claimedEntries.length > 0 && (
                  <div className="space-y-4">
                    <div className="flex items-center gap-3">
                      <h3 className="text-xl font-semibold text-slate-800">Claimed</h3>
                      <Badge variant="outline" className="bg-gray-50 text-gray-700 border-gray-200">
                        {claimedEntries.length} entries
                      </Badge>
                    </div>

                    <div className="grid gap-4">
                      {claimedEntries.map((entry, index) => (
                          <Card
                              key={entry.key || index}
                              className="border-gray-200 hover:shadow-lg transition-shadow bg-gray-50/50"
                          >
                            <CardHeader>
                              <div className="flex items-center justify-between">
                                <CardTitle className="text-lg text-slate-800">Message Entry</CardTitle>
                                <Badge variant="secondary" className="bg-gray-100 text-gray-700">
                                  <XCircle className="h-3 w-3 mr-1" />
                                  Claimed
                                </Badge>
                              </div>
                              <CardDescription className="flex items-center gap-2">
                                <Mail className="h-4 w-4" />
                                Message processed but payment data claimed
                              </CardDescription>
                            </CardHeader>
                            <CardContent>
                              <div className="p-3 bg-white rounded-lg border">
                                <p className="text-xs text-slate-500 mb-1">Message ID</p>
                                <p className="text-sm font-mono text-slate-700 break-all">{entry.value}</p>
                              </div>
                            </CardContent>
                          </Card>
                      ))}
                    </div>
                  </div>
              )}

              <div className="text-sm text-slate-600 pt-4 border-t border-slate-200">
                Total entries: {entries.length} ({notYetClaimedEntries.length} not yet claimed, {claimedEntries.length}{" "}
                claimed)
              </div>
            </div>
        )}
      </div>
  )
}
