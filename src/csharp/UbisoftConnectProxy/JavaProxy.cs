#nullable enable

using Microsoft.AspNetCore.Http;
using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using UbisoftConnectProxy.JavaInterop.Dtos;

namespace UbisoftConnectProxy
{
    public class JavaProxy : IProxy
    {
        private readonly Synchronizer _synchronizer;

        //private readonly DirectoryInfo _logFolder;
        //private volatile int _requestCounter;

        public JavaProxy(Synchronizer synchronizer)
        {
            _synchronizer = synchronizer;
            //_logFolder = new("Requests");
            //_logFolder.Create();
        }

        //private void LogFile(string name, object dto)
        //{
        //    var json = Newtonsoft.Json.JsonConvert.SerializeObject(dto, Newtonsoft.Json.Formatting.Indented);
        //    File.WriteAllText(Path.Combine(_logFolder.FullName, $"{name}.json"), json, Misc.UTF8NoBOM);
        //}

        public async Task ForwardAsync(HttpContext context)
        {
            byte[] content;
            await using (MemoryStream memoryStream = new())
            {
                await context.Request.Body.CopyToAsync(memoryStream);
                content = memoryStream.ToArray();
            }

            RequestDataDto requestDto = new()
            {
                Uri = "https://" + context.Request.Host + context.Request.Path +
                                    (context.Request.QueryString.HasValue ? context.Request.QueryString.Value : ""),
                Method = context.Request.Method,
                Content = content,
                Headers = context.Request.Headers
                    .Select(x => new HeaderDto { Name = x.Key, Values = x.Value.ToArray() })
                    .ToArray(),
            };

            Console.WriteLine($"Forwarding {requestDto.Method} {requestDto.Uri}");

            //var logName = $"{DateTime.Now.ToFileTime()}_{_requestCounter++}";
            //LogFile(logName + " 0Request", requestDto);

            var dto = await _synchronizer.SynchronizationContext.DispatchAsync(() => _synchronizer.Logic.DoRequestAsync(requestDto));

            //LogFile(logName + " 1Response", dto);

            try
            {
                foreach (var header in dto.Headers)
                {
                    if (header.Name == "Content-Type")
                    {
                        context.Response.ContentType = string.Join(",", header.Values);
                    }
                    else if (header.Name == "Content-Length")
                    {
                        context.Response.ContentLength = int.Parse(string.Join(",", header.Values));
                    }
                    else
                    {
                        foreach (var headerValue in header.Values)
                        {
                            context.Response.Headers.Add(header.Name, headerValue);
                            if (header.Name.ToLowerInvariant() == "strict-transport-security")
                            {
                                // exception when using multiple entries (also, wtf?)
                                break;
                            }
                        }
                    }
                }

                Console.WriteLine("Status of response: " + dto.StatusCode);
                context.Response.StatusCode = dto.StatusCode;

                await context.Response.Body.WriteAsync(dto.Content);
                await context.Response.Body.FlushAsync();
            }
            catch (Exception e)
            {
                _synchronizer.SynchronizationContext.Post(x =>
                {
                    _synchronizer.Logic.OnProxyError(e);
                }, null);
            }
        }
    }
}
